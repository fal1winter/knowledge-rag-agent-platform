"""面向复杂推理请求的迭代式检索。"""

from dataclasses import dataclass, field
from typing import List, Protocol

from rag_agent_platform.models import RetrievalHit


class Retriever(Protocol):
    def retrieve(self, tenant_id: str, query: str, material_ids: List[str] | None = None) -> List[RetrievalHit]:
        """针对单条查询检索证据。"""


class QueryPlanner(Protocol):
    def decompose(self, query: str, evidence: List[RetrievalHit]) -> List[str]:
        """生成追加子查询用于补充检索。"""


@dataclass
class IterationTrace:
    round_index: int
    query: str
    hit_count: int


@dataclass
class IterativeRetrievalResult:
    hits: List[RetrievalHit]
    trace: List[IterationTrace] = field(default_factory=list)


class AgenticRetriever:
    def __init__(self, retriever: Retriever, planner: QueryPlanner, max_rounds: int = 3):
        self.retriever = retriever
        self.planner = planner
        self.max_rounds = max_rounds

    def retrieve(self, tenant_id: str, query: str, material_ids: List[str] | None = None) -> IterativeRetrievalResult:
        pending = [query]
        all_hits: List[RetrievalHit] = []
        seen_chunks = set()
        trace: List[IterationTrace] = []
        for round_index in range(self.max_rounds):
            if not pending:
                break
            current_query = pending.pop(0)
            hits = self.retriever.retrieve(tenant_id, current_query, material_ids)
            new_hits = [hit for hit in hits if hit.chunk_id not in seen_chunks]
            for hit in new_hits:
                seen_chunks.add(hit.chunk_id)
            all_hits.extend(new_hits)
            trace.append(IterationTrace(round_index=round_index + 1, query=current_query, hit_count=len(new_hits)))
            if self._enough_evidence(all_hits):
                break
            pending.extend(self.planner.decompose(query, all_hits)[:2])
        return IterativeRetrievalResult(hits=all_hits, trace=trace)

    def _enough_evidence(self, hits: List[RetrievalHit]) -> bool:
        return len(hits) >= 8 or sum(hit.score for hit in hits[:5]) >= 4.0


class HeuristicQueryPlanner:
    """启发式子查询规划，生产环境使用 Qwen2.5-7B-Instruct。"""

    def decompose(self, query: str, evidence: List[RetrievalHit]) -> List[str]:
        if not evidence:
            return [f"{query} 背景", f"{query} 关键结论"]
        return [f"{query} 证据补充", f"{query} 反例或限制"]

