"""混合检索管线：Milvus 稠密 + Elasticsearch BM25 + RRF 融合 + BGE 精排。"""

from dataclasses import dataclass
from typing import List, Optional, Protocol

from rag_agent_platform.models import RetrievalHit
from rag_agent_platform.retrieval.elasticsearch_adapter import ElasticsearchBM25Retriever
from rag_agent_platform.retrieval.milvus_adapter import MilvusDenseRetriever
from rag_agent_platform.retrieval.reranker import BGEReranker
from rag_agent_platform.retrieval.rrf import RRFusion


class EmbeddingClient(Protocol):
    def embed(self, text: str) -> List[float]:
        """返回查询的稠密向量。"""


@dataclass
class HybridRetrievalConfig:
    top_k_dense: int = 30
    top_k_sparse: int = 30
    fused_top_k: int = 20
    final_top_k: int = 8


class HybridRetriever:
    def __init__(
        self,
        embeddings: EmbeddingClient,
        dense: MilvusDenseRetriever,
        sparse: ElasticsearchBM25Retriever,
        reranker: BGEReranker,
        fusion: RRFusion | None = None,
        config: HybridRetrievalConfig | None = None,
    ):
        self.embeddings = embeddings
        self.dense = dense
        self.sparse = sparse
        self.reranker = reranker
        self.fusion = fusion or RRFusion()
        self.config = config or HybridRetrievalConfig()

    def retrieve(
        self,
        tenant_id: str,
        query: str,
        material_ids: Optional[List[str]] = None,
    ) -> List[RetrievalHit]:
        query_vector = self.embeddings.embed(query)
        dense_hits = self.dense.search(tenant_id, query_vector, self.config.top_k_dense)
        sparse_hits = self.sparse.search(tenant_id, query, self.config.top_k_sparse, material_ids)
        fused = self.fusion.fuse([dense_hits, sparse_hits], top_k=self.config.fused_top_k)
        return self.reranker.rerank(query, fused, top_k=self.config.final_top_k)

