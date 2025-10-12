"""Bad Case 归因分类与复盘队列。"""

from collections import Counter
from dataclasses import dataclass, field
from typing import Dict, List

from rag_agent_platform.models import EvalCase, EvalScore


@dataclass
class BadCase:
    case_id: str
    category: str
    issues: List[str]
    query: str
    metadata: Dict = field(default_factory=dict)


class BadCaseAnalyzer:
    CATEGORY_MAP = {
        "empty_recall": "retrieval_recall_failure",
        "missing_citation_payload": "citation_pipeline_failure",
        "potential_unsupported_claim": "generation_faithfulness_failure",
        "judge_parse_error": "evaluation_infra_failure",
    }

    def classify(self, case: EvalCase, score: EvalScore) -> BadCase | None:
        if score.passed:
            return None
        categories = [self.CATEGORY_MAP.get(issue.split(":")[0], "other") for issue in score.issues]
        category = Counter(categories).most_common(1)[0][0] if categories else "low_score"
        return BadCase(
            case_id=case.case_id,
            category=category,
            issues=score.issues,
            query=case.query,
            metadata=case.metadata,
        )

    def build_review_queue(self, cases: List[EvalCase], scores: List[EvalScore]) -> List[BadCase]:
        queue = []
        for case, score in zip(cases, scores):
            bad_case = self.classify(case, score)
            if bad_case:
                queue.append(bad_case)
        return queue

