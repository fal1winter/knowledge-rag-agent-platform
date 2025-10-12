"""规则过滤器，捕获空召回、缺引文等明显失败。"""

from typing import List

from rag_agent_platform.models import EvalCase


class RuleBasedEvaluator:
    """检测空召回、引用缺失和引文-回答不一致。"""

    def evaluate(self, case: EvalCase) -> List[str]:
        issues: List[str] = []
        if not case.hits:
            issues.append("empty_recall")
        if case.answer and "[" in case.answer and not any(hit.citation for hit in case.hits):
            issues.append("missing_citation_payload")
        if "不知道" not in case.answer and not case.answer.strip():
            issues.append("empty_answer")
        for fact in case.expected_facts:
            if fact not in case.answer:
                issues.append(f"missing_expected_fact:{fact}")
        if self._has_unsupported_claim(case):
            issues.append("potential_unsupported_claim")
        return issues

    def _has_unsupported_claim(self, case: EvalCase) -> bool:
        if not case.answer or not case.hits:
            return False
        evidence_text = "\n".join(hit.text for hit in case.hits)
        answer_terms = {term for term in case.answer.split() if len(term) >= 4}
        evidence_terms = {term for term in evidence_text.split() if len(term) >= 4}
        if not answer_terms:
            return False
        overlap = len(answer_terms & evidence_terms) / len(answer_terms)
        return overlap < 0.2

