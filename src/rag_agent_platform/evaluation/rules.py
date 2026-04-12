"""规则过滤器，捕获空召回、缺引文等明显失败。

在 LLM Judge 评分之前快速检测明显的系统级故障，
避免将已知缺陷送入耗时的模型评测流程。

规则分层设计：
- 硬规则 (HARD): 命中即判定失败，不再送 Judge 评测
- 软规则 (SOFT): 命中记录 issue 但仍送 Judge 评测，辅助归因

可扩展性：
- 通过 register_rule() 动态注册自定义规则
- 规则执行顺序按注册顺序，可通过 priority 调整
"""

from dataclasses import dataclass
from enum import Enum
from typing import Callable, Dict, List, Optional

from rag_agent_platform.models import EvalCase


class RuleLevel(str, Enum):
    """规则严格程度。"""
    HARD = "hard"   # 命中即判定失败
    SOFT = "soft"   # 记录 issue，辅助归因


@dataclass
class Rule:
    """单条评测规则定义。"""
    name: str
    level: RuleLevel
    check: Callable[[EvalCase], Optional[str]]
    description: str = ""
    priority: int = 100  # 数字越小优先级越高


@dataclass
class RuleCheckResult:
    """规则检查结果。"""
    case_id: str
    issues: List[str]
    hard_fail: bool  # 是否命中硬规则，直接判定失败
    triggered_rules: List[str]

    @property
    def passed_hard_rules(self) -> bool:
        return not self.hard_fail


class RuleBasedEvaluator:
    """基于规则的评测前置过滤器。

    检测空召回、引用缺失、回答为空、内容不一致等明显失败，
    减少不必要的 LLM Judge 调用，加速评测流水线。

    使用方式：
        evaluator = RuleBasedEvaluator()
        evaluator.register_rule(Rule(...))  # 可选：注册自定义规则
        result = evaluator.evaluate_case(case)
        if result.hard_fail:
            # 跳过 Judge 评测，直接标记失败
    """

    def __init__(self):
        self._rules: List[Rule] = []
        self._register_default_rules()

    def _register_default_rules(self):
        """注册内置默认规则集。"""
        self.register_rule(Rule(
            name="empty_recall",
            level=RuleLevel.HARD,
            check=self._check_empty_recall,
            description="检索结果为空，未召回任何文档",
            priority=10,
        ))
        self.register_rule(Rule(
            name="empty_answer",
            level=RuleLevel.HARD,
            check=self._check_empty_answer,
            description="模型返回空白回答",
            priority=20,
        ))
        self.register_rule(Rule(
            name="missing_citation_payload",
            level=RuleLevel.SOFT,
            check=self._check_missing_citation,
            description="回答中引用了来源但检索结果中缺少引文载荷",
            priority=50,
        ))
        self.register_rule(Rule(
            name="missing_expected_fact",
            level=RuleLevel.SOFT,
            check=self._check_expected_facts,
            description="回答中缺少期望的关键事实",
            priority=60,
        ))
        self.register_rule(Rule(
            name="potential_unsupported_claim",
            level=RuleLevel.SOFT,
            check=self._check_unsupported_claim,
            description="回答中可能存在超出检索证据的断言",
            priority=70,
        ))
        self.register_rule(Rule(
            name="answer_too_short",
            level=RuleLevel.SOFT,
            check=self._check_answer_too_short,
            description="回答过短，可能信息不完整",
            priority=80,
        ))
        self.register_rule(Rule(
            name="excessive_repetition",
            level=RuleLevel.SOFT,
            check=self._check_repetition,
            description="回答存在大段重复内容",
            priority=90,
        ))

    def register_rule(self, rule: Rule):
        """注册自定义规则，按 priority 排序插入。"""
        self._rules.append(rule)
        self._rules.sort(key=lambda r: r.priority)

    def evaluate(self, case: EvalCase) -> List[str]:
        """兼容旧接口：返回 issue 字符串列表。"""
        result = self.evaluate_case(case)
        return result.issues

    def evaluate_case(self, case: EvalCase) -> RuleCheckResult:
        """对单条 case 执行全部规则检查。"""
        issues: List[str] = []
        hard_fail = False
        triggered: List[str] = []

        for rule in self._rules:
            issue = rule.check(case)
            if issue:
                issues.append(issue)
                triggered.append(rule.name)
                if rule.level == RuleLevel.HARD:
                    hard_fail = True

        return RuleCheckResult(
            case_id=case.case_id,
            issues=issues,
            hard_fail=hard_fail,
            triggered_rules=triggered,
        )

    def batch_evaluate(self, cases: List[EvalCase]) -> Dict[str, RuleCheckResult]:
        """批量评测，返回 case_id -> RuleCheckResult 映射。"""
        return {case.case_id: self.evaluate_case(case) for case in cases}

    def get_hard_failures(self, cases: List[EvalCase]) -> List[EvalCase]:
        """筛出命中硬规则的 case，这些不需要再送 LLM Judge。"""
        return [
            case for case in cases
            if self.evaluate_case(case).hard_fail
        ]

    def get_soft_only(self, cases: List[EvalCase]) -> List[EvalCase]:
        """筛出只命中软规则或无问题的 case，这些继续送 LLM Judge。"""
        return [
            case for case in cases
            if not self.evaluate_case(case).hard_fail
        ]

    # --- 内置规则实现 ---

    @staticmethod
    def _check_empty_recall(case: EvalCase) -> Optional[str]:
        if not case.hits:
            return "empty_recall"
        return None

    @staticmethod
    def _check_empty_answer(case: EvalCase) -> Optional[str]:
        if not case.answer or not case.answer.strip():
            # "不知道" 类回答不算空
            if case.answer and "不知道" in case.answer:
                return None
            return "empty_answer"
        return None

    @staticmethod
    def _check_missing_citation(case: EvalCase) -> Optional[str]:
        if case.answer and "[" in case.answer and "]" in case.answer:
            has_citation_data = any(
                getattr(hit, "citation", None) for hit in case.hits
            )
            if not has_citation_data:
                return "missing_citation_payload"
        return None

    @staticmethod
    def _check_expected_facts(case: EvalCase) -> Optional[str]:
        """检查期望事实是否出现在回答中。返回第一个缺失的事实。"""
        missing = []
        for fact in case.expected_facts:
            if fact not in case.answer:
                missing.append(fact)
        if missing:
            return f"missing_expected_fact:{missing[0]}"
        return None

    @staticmethod
    def _check_unsupported_claim(case: EvalCase) -> Optional[str]:
        """检测回答是否包含检索证据未覆盖的内容（基于词汇重叠度）。"""
        if not case.answer or not case.hits:
            return None
        evidence_text = "\n".join(hit.text for hit in case.hits)
        # 提取有意义的词（长度 >= 4 的中文分词或英文词）
        answer_terms = {term for term in case.answer.split() if len(term) >= 4}
        evidence_terms = {term for term in evidence_text.split() if len(term) >= 4}
        if not answer_terms:
            return None
        overlap = len(answer_terms & evidence_terms) / len(answer_terms)
        if overlap < 0.2:
            return "potential_unsupported_claim"
        return None

    @staticmethod
    def _check_answer_too_short(case: EvalCase) -> Optional[str]:
        """回答过短（少于 10 字符且不是拒答）可能信息不充分。"""
        if not case.answer:
            return None
        stripped = case.answer.strip()
        if len(stripped) < 10 and "不知道" not in stripped:
            return "answer_too_short"
        return None

    @staticmethod
    def _check_repetition(case: EvalCase) -> Optional[str]:
        """检测回答中大段重复（同一句子出现 3 次以上）。"""
        if not case.answer:
            return None
        sentences = [s.strip() for s in case.answer.replace("。", "\n").split("\n") if s.strip()]
        if len(sentences) < 3:
            return None
        from collections import Counter
        freq = Counter(sentences)
        for sentence, count in freq.most_common(1):
            if count >= 3 and len(sentence) > 5:
                return "excessive_repetition"
        return None
