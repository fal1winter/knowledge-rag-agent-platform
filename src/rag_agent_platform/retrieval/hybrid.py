"""混合检索管线：Milvus 稠密 + Elasticsearch BM25 + RRF 融合 + BGE 精排。

检索流程（四阶段）：
  1. 向量化：BGE-large-zh 将查询编码为 1024 维稠密向量
  2. 双路召回：Milvus ANN 稠密检索 + Elasticsearch BM25 稀疏检索
  3. RRF 融合：Reciprocal Rank Fusion 合并两路结果，消除分布差异
  4. 精排：BGE-reranker-v2-m3 交叉编码器对融合结果重排序
"""

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
    """各阶段的 top-k 控制参数。

    召回阶段宽取（30），融合后收窄（20），精排进一步筛选（8）。
    逐级收窄既保证召回率，又控制精排阶段的计算开销。
    """
    top_k_dense: int = 30       # Milvus ANN 召回数
    top_k_sparse: int = 30      # ES BM25 召回数
    fused_top_k: int = 20       # RRF 融合后保留数
    final_top_k: int = 8        # 精排后最终返回数


class HybridRetriever:
    """四阶段混合检索器，平衡语义匹配和词汇精确匹配。"""

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
        # Stage 1: 查询向量化
        query_vector = self.embeddings.embed(query)

        # Stage 2: 双路并行召回
        dense_hits = self.dense.search(tenant_id, query_vector, self.config.top_k_dense)
        sparse_hits = self.sparse.search(tenant_id, query, self.config.top_k_sparse, material_ids)

        # Stage 3: RRF 融合去重
        fused = self.fusion.fuse([dense_hits, sparse_hits], top_k=self.config.fused_top_k)

        # Stage 4: 交叉编码器精排
        return self.reranker.rerank(query, fused, top_k=self.config.final_top_k)

