"""完整 RAG 评测流水线：规则过滤 → LLM 打分 → Bad Case 归因。

评测流程分三阶段：
1. 规则预筛：快速检测空召回、缺引文等硬伤（零 LLM 调用）
2. LLM-as-Judge：对存活 Case 逐条打分（相关性、忠实度、完整性、引文质量）
3. Bad Case 归因：自动分类失败原因、按严重度排序生成人工审阅队列

支持批量评测和增量评测两种模式。
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from rag_agent_platform.evaluation.badcase import BadCase, BadCaseAnalyzer
from rag_agent_platform.evaluation.llm_judge import LLMJudge
from rag_agent_platform.evaluation.rules import RuleBasedEvaluator
from rag_agent_platform.models import EvalCase, EvalScore

logger = logging.getLogger(__name__)


@dataclass
class EvalMetricsSummary:
    """批量评测的聚合指标。"""
    total_cases: int
    passed: int
    failed: int
    avg_relevance: float
    avg_faithfulness: float
    avg_completeness: float
    avg_citation_quality: float
    pass_rate: float
    elapsed_ms: float
    issue_distribution: Dict[str, int] = field(default_factory=dict)


@dataclass
class EvalReport:
    """完整评测报告，包含分数、Bad Case 和聚合指标。"""
    scores: List[EvalScore]
    bad_cases: List[BadCase]
    summary: EvalMetricsSummary
    metadata: Dict = field(default_factory=dict)


class EvaluationPipeline:
    """端到端评测管线。

    配置参数：
    - pass_threshold: 综合评分高于此值视为通过（默认 0.6）
    - skip_llm_on_hard_fail: 规则检测到硬伤时跳过 LLM 打分，直接标记失败
    - max_concurrent_judges: LLM 打分并行度（单线程默认）
    """

    def __init__(
        self,
        rules: RuleBasedEvaluator | None = None,
        judge: LLMJudge | None = None,
        badcase_analyzer: BadCaseAnalyzer | None = None,
        pass_threshold: float = 0.6,
        skip_llm_on_hard_fail: bool = True,
    ):
        self.rules = rules or RuleBasedEvaluator()
        self.judge = judge or LLMJudge()
        self.badcase_analyzer = badcase_analyzer or BadCaseAnalyzer()
        self.pass_threshold = pass_threshold
        self.skip_llm_on_hard_fail = skip_llm_on_hard_fail

    def evaluate(self, cases: List[EvalCase]) -> tuple[List[EvalScore], List[BadCase]]:
        """对一批 Case 执行完整评测，返回分数和 Bad Case 列表。"""
        scores = []
        for case in cases:
            issues = self.rules.evaluate(case)
            # 硬伤直接标记失败，省 LLM 调用
            if self.skip_llm_on_hard_fail and self._is_hard_fail(issues):
                scores.append(self._zero_score(case, issues))
            else:
                scores.append(self.judge.judge(case, issues))
        return scores, self.badcase_analyzer.build_review_queue(cases, scores)

    def evaluate_with_report(self, cases: List[EvalCase], run_id: Optional[str] = None) -> EvalReport:
        """执行评测并生成完整报告（含聚合指标）。"""
        t0 = time.perf_counter()
        scores, bad_cases = self.evaluate(cases)
        elapsed = (time.perf_counter() - t0) * 1000

        summary = self._aggregate(scores, elapsed)
        logger.info(
            "评测完成: %d cases, pass_rate=%.2f, avg_relevance=%.3f, %.0fms",
            summary.total_cases, summary.pass_rate, summary.avg_relevance, elapsed,
        )
        return EvalReport(
            scores=scores,
            bad_cases=bad_cases,
            summary=summary,
            metadata={"run_id": run_id, "threshold": self.pass_threshold},
        )

    def _aggregate(self, scores: List[EvalScore], elapsed: float) -> EvalMetricsSummary:
        """对所有 Case 的分数进行聚合统计。"""
        if not scores:
            return EvalMetricsSummary(0, 0, 0, 0, 0, 0, 0, 0, elapsed)

        passed = sum(1 for s in scores if s.passed)
        issue_dist: Dict[str, int] = {}
        for s in scores:
            for issue in s.issues:
                tag = issue.split(":")[0]
                issue_dist[tag] = issue_dist.get(tag, 0) + 1

        n = len(scores)
        return EvalMetricsSummary(
            total_cases=n,
            passed=passed,
            failed=n - passed,
            avg_relevance=sum(s.relevance for s in scores) / n,
            avg_faithfulness=sum(s.faithfulness for s in scores) / n,
            avg_completeness=sum(s.completeness for s in scores) / n,
            avg_citation_quality=sum(s.citation_quality for s in scores) / n,
            pass_rate=passed / n,
            elapsed_ms=elapsed,
            issue_distribution=issue_dist,
        )

    def _is_hard_fail(self, issues: List[str]) -> bool:
        """规则预筛阶段检测到的硬伤（无需 LLM 就能判定失败）。"""
        hard_fail_tags = {"empty_recall", "empty_answer"}
        return bool(hard_fail_tags & set(issues))

    def _zero_score(self, case: EvalCase, issues: List[str]) -> EvalScore:
        """硬伤 Case 直接给零分。"""
        return EvalScore(
            case_id=case.case_id,
            relevance=0.0,
            faithfulness=0.0,
            completeness=0.0,
            citation_quality=0.0,
            passed=False,
            issues=issues,
        )

