"""面向检索链路灰度发布的 Pairwise A/B 对比评测。"""

from dataclasses import dataclass
from typing import List

from rag_agent_platform.models import EvalCase, EvalScore


@dataclass
class PairwiseResult:
    case_id: str
    winner: str
    reason: str
    score_delta: float


class PairwiseABTester:
    def compare(self, case_a: EvalCase, score_a: EvalScore, case_b: EvalCase, score_b: EvalScore) -> PairwiseResult:
        total_a = self._total(score_a)
        total_b = self._total(score_b)
        if abs(total_a - total_b) < 0.03:
            winner = "tie"
        else:
            winner = "A" if total_a > total_b else "B"
        return PairwiseResult(
            case_id=case_a.case_id,
            winner=winner,
            reason=f"A={total_a:.3f}, B={total_b:.3f}",
            score_delta=total_a - total_b,
        )

    def batch_compare(self, a_cases: List[EvalCase], a_scores: List[EvalScore], b_cases: List[EvalCase], b_scores: List[EvalScore]) -> List[PairwiseResult]:
        return [self.compare(a, sa, b, sb) for a, sa, b, sb in zip(a_cases, a_scores, b_cases, b_scores)]

    def _total(self, score: EvalScore) -> float:
        return (
            0.3 * score.relevance
            + 0.35 * score.faithfulness
            + 0.25 * score.completeness
            + 0.1 * score.citation_quality
        )

