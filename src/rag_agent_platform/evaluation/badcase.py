"""Bad Case 归因分类与复盘队列。

对评测不合格的 Case 进行根因归类，生成复盘优先级队列，
帮助开发者快速定位系统薄弱环节并决策优化方向。

分类维度：
- retrieval_recall_failure: 检索未召回相关文档
- citation_pipeline_failure: 引文管道异常，未正确附带来源
- generation_faithfulness_failure: 生成内容超出检索证据范围
- evaluation_infra_failure: 评测基础设施自身故障
- low_score: 无明确归因，综合得分低
"""

from collections import Counter
from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional

from rag_agent_platform.models import EvalCase, EvalScore


class Severity(str, Enum):
    """Bad case 严重程度，决定复盘优先级。"""
    CRITICAL = "critical"   # 完全错误回答或安全风险
    HIGH = "high"           # 关键信息缺失
    MEDIUM = "medium"       # 部分信息不准确
    LOW = "low"             # 可改进但不影响使用


@dataclass
class BadCase:
    """单条不合格 Case 的归因记录。"""
    case_id: str
    category: str
    severity: Severity
    issues: List[str]
    query: str
    suggested_action: str = ""
    metadata: Dict = field(default_factory=dict)


@dataclass
class BadCaseReport:
    """Bad case 分析报告，汇总各类失败的分布和趋势。"""
    total_cases: int
    bad_count: int
    category_distribution: Dict[str, int]
    severity_distribution: Dict[str, int]
    top_issues: List[str]
    suggested_priorities: List[str]

    @property
    def bad_rate(self) -> float:
        return self.bad_count / self.total_cases if self.total_cases > 0 else 0.0

    def summary(self) -> str:
        lines = [
            f"Bad Case 报告: {self.bad_count}/{self.total_cases} ({self.bad_rate:.1%})",
            f"  严重程度分布: {self.severity_distribution}",
            f"  分类分布: {self.category_distribution}",
            "  优先修复建议:",
        ]
        for priority in self.suggested_priorities[:5]:
            lines.append(f"    - {priority}")
        return "\n".join(lines)


class BadCaseAnalyzer:
    """Bad case 归因分析器。

    职责：
    1. 将评测失败 case 归类到预定义失败类型
    2. 评估严重程度
    3. 生成修复建议
    4. 构建优先级复盘队列
    """

    CATEGORY_MAP = {
        "empty_recall": "retrieval_recall_failure",
        "missing_citation_payload": "citation_pipeline_failure",
        "potential_unsupported_claim": "generation_faithfulness_failure",
        "judge_parse_error": "evaluation_infra_failure",
        "empty_answer": "generation_failure",
        "missing_expected_fact": "retrieval_precision_failure",
        "answer_too_long": "generation_verbosity",
        "answer_too_short": "generation_brevity",
    }

    SEVERITY_RULES = {
        "retrieval_recall_failure": Severity.CRITICAL,
        "generation_faithfulness_failure": Severity.CRITICAL,
        "citation_pipeline_failure": Severity.HIGH,
        "generation_failure": Severity.HIGH,
        "retrieval_precision_failure": Severity.MEDIUM,
        "evaluation_infra_failure": Severity.LOW,
        "generation_verbosity": Severity.LOW,
        "generation_brevity": Severity.MEDIUM,
    }

    ACTION_SUGGESTIONS = {
        "retrieval_recall_failure": "检查文档切片策略和向量化质量，考虑增加召回 top_k",
        "citation_pipeline_failure": "检查引文抽取正则和 chunk metadata 完整性",
        "generation_faithfulness_failure": "加强 prompt 中的忠实性约束，考虑添加 NLI 验证",
        "generation_failure": "检查 LLM 调用是否超时或返回空，确认 prompt 模板完整",
        "retrieval_precision_failure": "优化 reranker 阈值或增加精排候选数",
        "evaluation_infra_failure": "检查评测 judge 模型的输出格式解析逻辑",
        "generation_verbosity": "调整 max_tokens 或在 prompt 中约束回答长度",
        "generation_brevity": "检查是否 truncation 过早或 prompt 鼓励简短回答",
    }

    def classify(self, case: EvalCase, score: EvalScore) -> Optional[BadCase]:
        """对单条 case 进行归因分类。通过评测的 case 返回 None。"""
        if score.passed:
            return None

        categories = [
            self.CATEGORY_MAP.get(issue.split(":")[0], "other")
            for issue in score.issues
        ]
        category = Counter(categories).most_common(1)[0][0] if categories else "low_score"
        severity = self._assess_severity(category, score)
        suggested_action = self.ACTION_SUGGESTIONS.get(category, "人工复盘分析")

        return BadCase(
            case_id=case.case_id,
            category=category,
            severity=severity,
            issues=score.issues,
            query=case.query,
            suggested_action=suggested_action,
            metadata={**case.metadata, "scores": score.dimension_scores},
        )

    def _assess_severity(self, category: str, score: EvalScore) -> Severity:
        """根据分类和分数综合判断严重程度。"""
        base_severity = self.SEVERITY_RULES.get(category, Severity.MEDIUM)

        # 如果综合得分极低，升级严重程度
        if score.overall_score is not None and score.overall_score < 0.2:
            if base_severity in (Severity.MEDIUM, Severity.LOW):
                return Severity.HIGH
        return base_severity

    def build_review_queue(
        self, cases: List[EvalCase], scores: List[EvalScore]
    ) -> List[BadCase]:
        """构建优先级复盘队列，按严重程度排序。"""
        severity_order = {
            Severity.CRITICAL: 0,
            Severity.HIGH: 1,
            Severity.MEDIUM: 2,
            Severity.LOW: 3,
        }

        queue = []
        for case, score in zip(cases, scores):
            bad_case = self.classify(case, score)
            if bad_case:
                queue.append(bad_case)

        queue.sort(key=lambda bc: severity_order.get(bc.severity, 99))
        return queue

    def generate_report(
        self, cases: List[EvalCase], scores: List[EvalScore]
    ) -> BadCaseReport:
        """生成 bad case 分析报告，汇总失败分布和优化建议。"""
        queue = self.build_review_queue(cases, scores)

        category_dist = Counter(bc.category for bc in queue)
        severity_dist = Counter(bc.severity.value for bc in queue)

        # 提取高频 issue
        all_issues = []
        for bc in queue:
            all_issues.extend(bc.issues)
        top_issues = [issue for issue, _ in Counter(all_issues).most_common(10)]

        # 根据分类频率生成优先修复建议
        priorities = []
        for category, count in category_dist.most_common():
            action = self.ACTION_SUGGESTIONS.get(category, "人工复盘分析")
            priorities.append(f"[{count}次] {category}: {action}")

        return BadCaseReport(
            total_cases=len(cases),
            bad_count=len(queue),
            category_distribution=dict(category_dist),
            severity_distribution=dict(severity_dist),
            top_issues=top_issues,
            suggested_priorities=priorities,
        )
