"""LLM-as-Judge 评分：相关性、忠实度、完整性。"""

import json
from typing import Dict, List, Protocol

from rag_agent_platform.models import EvalCase, EvalScore


class JudgeModel(Protocol):
    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.0) -> str:
        """返回 JSON 格式的评测结果。"""


class LLMJudge:
    def __init__(self, model: JudgeModel | None = None, allow_heuristic: bool = True):
        self.model = model
        self.allow_heuristic = allow_heuristic

    def judge(self, case: EvalCase, rule_issues: List[str] | None = None) -> EvalScore:
        if self.model is None:
            if not self.allow_heuristic:
                raise RuntimeError("LLM judge model is not configured")
            return self._heuristic_judge(case, rule_issues or [])
        prompt = self._build_prompt(case)
        raw = self.model.complete(prompt, temperature=0.0)
        data = self._parse(raw)
        issues = list(rule_issues or []) + data.get("issues", [])
        return EvalScore(
            case_id=case.case_id,
            relevance=float(data.get("relevance", 0)),
            faithfulness=float(data.get("faithfulness", 0)),
            completeness=float(data.get("completeness", 0)),
            citation_quality=float(data.get("citation_quality", 0)),
            passed=not issues and min(
                float(data.get("relevance", 0)),
                float(data.get("faithfulness", 0)),
                float(data.get("completeness", 0)),
            ) >= 0.75,
            issues=issues,
        )

    def _heuristic_judge(self, case: EvalCase, rule_issues: List[str]) -> EvalScore:
        answer_terms = set(case.answer.lower().split())
        evidence_terms = set(" ".join(hit.text for hit in case.hits).lower().split())
        query_terms = set(case.query.lower().split())
        expected_terms = set(" ".join(case.expected_facts).lower().split())
        relevance = len(query_terms & answer_terms) / max(1, len(query_terms))
        faithfulness = len(answer_terms & evidence_terms) / max(1, len(answer_terms)) if answer_terms else 0.0
        completeness = len(expected_terms & answer_terms) / max(1, len(expected_terms)) if expected_terms else min(1.0, len(answer_terms) / 20)
        citation_quality = min(1.0, len([hit for hit in case.hits if hit.citation]) / max(1, len(case.hits)))
        issues = list(rule_issues)
        if faithfulness < 0.5:
            issues.append("low_faithfulness_heuristic")
        return EvalScore(
            case_id=case.case_id,
            relevance=relevance,
            faithfulness=faithfulness,
            completeness=completeness,
            citation_quality=citation_quality,
            passed=not issues and min(relevance, faithfulness, completeness) >= 0.75,
            issues=issues,
        )

    def _build_prompt(self, case: EvalCase) -> List[Dict[str, str]]:
        evidence = "\n".join(f"- {hit.text}" for hit in case.hits)
        return [
            {"role": "system", "content": "你是 RAG 评测专家。按 relevance、faithfulness、completeness、citation_quality 输出 0-1 JSON。"},
            {"role": "user", "content": f"问题：{case.query}\n回答：{case.answer}\n证据：{evidence}"},
        ]

    def _parse(self, raw: str) -> Dict:
        try:
            return json.loads(raw)
        except Exception:
            return {"relevance": 0.0, "faithfulness": 0.0, "completeness": 0.0, "citation_quality": 0.0, "issues": ["judge_parse_error"]}
