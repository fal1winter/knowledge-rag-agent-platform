"""RAPTOR 两阶段检索：粗召回 + 精准下钻。"""

from dataclasses import dataclass
from typing import Iterable, List, Protocol

from rag_agent_platform.models import Chunk, RetrievalHit
from rag_agent_platform.raptor.tree_index import EmbeddingClient, InMemoryRaptorStore


class VectorSearchBackend(Protocol):
    def search(self, tenant_id: str, query_vector: List[float], top_k: int, level: int | None = None) -> List[RetrievalHit]:
        """在稠密索引中搜索，可按 RAPTOR 层级过滤。"""


@dataclass
class RaptorRetrievalConfig:
    coarse_top_k: int = 8
    drilldown_top_k: int = 12
    final_top_k: int = 6


class RaptorRetriever:
    def __init__(
        self,
        embeddings: EmbeddingClient,
        vector_backend: VectorSearchBackend,
        tree_store: InMemoryRaptorStore,
        config: RaptorRetrievalConfig | None = None,
    ):
        self.embeddings = embeddings
        self.vector_backend = vector_backend
        self.tree_store = tree_store
        self.config = config or RaptorRetrievalConfig()

    def retrieve(self, tenant_id: str, query: str) -> List[RetrievalHit]:
        query_vector = self.embeddings.embed(query)
        max_summary_level = max((node.level for node in self.tree_store.nodes.values()), default=0)
        summary_level = max_summary_level if max_summary_level > 0 else 0
        summary_hits = self.vector_backend.search(
            tenant_id=tenant_id,
            query_vector=query_vector,
            top_k=self.config.coarse_top_k,
            level=summary_level,
        )
        candidate_leaves = self._expand_to_leaves(summary_hits)
        if not candidate_leaves:
            return []
        rescored = self._score_leaves(query_vector, candidate_leaves)
        return rescored[: self.config.final_top_k]

    def _expand_to_leaves(self, summary_hits: Iterable[RetrievalHit]) -> List[Chunk]:
        leaves: List[Chunk] = []
        seen = set()
        for hit in summary_hits:
            node = self.tree_store.get(hit.chunk_id)
            if node is None:
                continue
            stack = [node]
            while stack:
                current = stack.pop()
                children = self.tree_store.children(current.chunk_id)
                if not children and current.chunk_id not in seen:
                    leaves.append(current)
                    seen.add(current.chunk_id)
                else:
                    stack.extend(children)
        return leaves

    def _score_leaves(self, query_vector: List[float], leaves: List[Chunk]) -> List[RetrievalHit]:
        hits = []
        for leaf in leaves:
            score = self._cosine(query_vector, leaf.vector or [])
            hits.append(
                RetrievalHit(
                    chunk_id=leaf.chunk_id,
                    document_id=leaf.document_id,
                    text=leaf.text,
                    score=score,
                    source="raptor_drilldown",
                    tenant_id=leaf.tenant_id,
                    citation={"document_id": leaf.document_id, "page": leaf.page},
                    metadata=leaf.metadata,
                )
            )
        return sorted(hits, key=lambda hit: hit.score, reverse=True)

    def _cosine(self, left: List[float], right: List[float]) -> float:
        if not left or not right:
            return 0.0
        dot = sum(a * b for a, b in zip(left, right))
        left_norm = sum(a * a for a in left) ** 0.5
        right_norm = sum(b * b for b in right) ** 0.5
        if left_norm == 0 or right_norm == 0:
            return 0.0
        return dot / (left_norm * right_norm)

