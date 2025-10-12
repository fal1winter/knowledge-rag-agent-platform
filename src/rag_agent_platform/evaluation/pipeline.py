"""完整 RAG 评测流水线：规则过滤 → LLM 打分 → Bad Case 归因。"""

from typing import List

from rag_agent_platform.evaluation.badcase import BadCase, BadCaseAnalyzer
from rag_agent_platform.evaluation.llm_judge import LLMJudge
from rag_agent_platform.evaluation.rules import RuleBasedEvaluator
from rag_agent_platform.models import EvalCase, EvalScore


class EvaluationPipeline:
    def __init__(
        self,
        rules: RuleBasedEvaluator | None = None,
        judge: LLMJudge | None = None,
        badcase_analyzer: BadCaseAnalyzer | None = None,
    ):
        self.rules = rules or RuleBasedEvaluator()
        self.judge = judge or LLMJudge()
        self.badcase_analyzer = badcase_analyzer or BadCaseAnalyzer()

    def evaluate(self, cases: List[EvalCase]) -> tuple[List[EvalScore], List[BadCase]]:
        scores = []
        for case in cases:
            issues = self.rules.evaluate(case)
            scores.append(self.judge.judge(case, issues))
        return scores, self.badcase_analyzer.build_review_queue(cases, scores)

