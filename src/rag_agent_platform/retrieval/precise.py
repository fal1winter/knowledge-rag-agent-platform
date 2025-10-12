"""面向简单知识库问答的精准块检索。"""

from dataclasses import dataclass
from typing import List, Optional

from rag_agent_platform.models import RetrievalHit
from rag_agent_platform.retrieval.hybrid import HybridRetriever


@dataclass
class PreciseBlockConfig:
    candidate_top_k: int = 8
    final_top_k: int = 4
    min_score: float | None = None


class PreciseBlockRetriever:
    """为直接问答返回紧凑的叶子级原文块。"""

    def __init__(self, hybrid_retriever: HybridRetriever, config: PreciseBlockConfig | None = None):
        self.hybrid_retriever = hybrid_retriever
        self.config = config or PreciseBlockConfig()

    def retrieve(
        self,
        tenant_id: str,
        query: str,
        material_ids: Optional[List[str]] = None,
    ) -> List[RetrievalHit]:
        hits = self.hybrid_retriever.retrieve(tenant_id, query, material_ids)
        leaf_hits = [
            hit
            for hit in hits[: self.config.candidate_top_k]
            if hit.metadata.get("node_type") != "raptor_summary"
        ]
        if self.config.min_score is not None:
            leaf_hits = [hit for hit in leaf_hits if hit.score >= self.config.min_score]
        return leaf_hits[: self.config.final_top_k]
