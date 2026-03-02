"""检索模块单元测试。"""

import pytest

from rag_agent_platform.models import Chunk, RetrievalHit
from rag_agent_platform.retrieval.rrf import RRFusion
from rag_agent_platform.retrieval.precise import PreciseBlockRetriever
from rag_agent_platform.storage.in_memory import HashEmbeddingClient, InMemoryDenseBackend, InMemorySparseBackend


class TestRRFusion:
    def test_fuse_merges_ranked_lists(self):
        hit_a = RetrievalHit("c1", "d1", "text a", 0.9, "dense", "t")
        hit_b = RetrievalHit("c2", "d2", "text b", 0.8, "sparse", "t")
        fused = RRFusion().fuse([[hit_a], [hit_b]], top_k=2)
        assert len(fused) == 2
        assert all(h.score > 0 for h in fused)

    def test_fuse_deduplicates_same_chunk(self):
        hit_a = RetrievalHit("c1", "d1", "text", 0.9, "dense", "t")
        hit_b = RetrievalHit("c1", "d1", "text", 0.7, "sparse", "t")
        fused = RRFusion().fuse([[hit_a], [hit_b]], top_k=5)
        assert len(fused) == 1
        assert fused[0].chunk_id == "c1"

    def test_fuse_respects_top_k(self):
        hits = [RetrievalHit(f"c{i}", "d1", f"text {i}", 0.9 - i * 0.1, "dense", "t") for i in range(10)]
        fused = RRFusion().fuse([hits], top_k=3)
        assert len(fused) == 3

    def test_fuse_empty_lists(self):
        fused = RRFusion().fuse([[], []], top_k=5)
        assert fused == []


class TestInMemoryDenseBackend:
    def test_upsert_and_search(self):
        backend = InMemoryDenseBackend()
        embeddings = HashEmbeddingClient()
        chunks = [
            Chunk("c1", "doc-1", "Milvus 向量搜索", "t-a", vector=embeddings.embed("Milvus 向量搜索")),
            Chunk("c2", "doc-1", "Elasticsearch 全文检索", "t-a", vector=embeddings.embed("Elasticsearch 全文检索")),
        ]
        backend.upsert_vectors(chunks)
        query_vector = embeddings.embed("向量搜索")
        hits = backend.search("t-a", query_vector, top_k=2)
        assert len(hits) > 0
        assert all(isinstance(h, RetrievalHit) for h in hits)

    def test_tenant_isolation(self):
        backend = InMemoryDenseBackend()
        embeddings = HashEmbeddingClient()
        chunk_a = Chunk("c1", "doc-1", "text", "tenant-a", vector=embeddings.embed("text"))
        chunk_b = Chunk("c2", "doc-2", "text", "tenant-b", vector=embeddings.embed("text"))
        backend.upsert_vectors([chunk_a, chunk_b])
        hits = backend.search("tenant-a", embeddings.embed("text"), top_k=5)
        assert all(h.tenant_id == "tenant-a" for h in hits)


class TestInMemorySparseBackend:
    def test_upsert_and_search(self):
        backend = InMemorySparseBackend()
        chunks = [
            Chunk("c1", "doc-1", "Milvus 向量数据库支持近似搜索", "t"),
            Chunk("c2", "doc-1", "无关内容完全不同", "t"),
        ]
        backend.upsert_documents(chunks)
        hits = backend.search("t", "Milvus 向量", top_k=2)
        assert hits[0].chunk_id == "c1"
        assert hits[0].score > hits[1].score

    def test_tenant_isolation(self):
        backend = InMemorySparseBackend()
        backend.upsert_documents([
            Chunk("c1", "d1", "共享关键词", "tenant-a"),
            Chunk("c2", "d2", "共享关键词", "tenant-b"),
        ])
        hits = backend.search("tenant-a", "共享关键词", top_k=5)
        assert all(h.tenant_id == "tenant-a" for h in hits)


class TestHybridRetriever:
    def test_hybrid_retriever_returns_results(self):
        """通过 mock 组件验证 HybridRetriever 编排逻辑。"""
        from rag_agent_platform.retrieval.hybrid import HybridRetriever
        from rag_agent_platform.retrieval.rrf import RRFusion

        class MockEmbedding:
            def embed(self, text):
                return [0.1] * 64

        class MockDense:
            def search(self, tenant_id, query_vector, top_k, level=None):
                return [RetrievalHit("c1", "d1", "dense result", 0.9, "dense", tenant_id)]

        class MockSparse:
            def search(self, tenant_id, query, top_k, material_ids=None):
                return [RetrievalHit("c2", "d1", "sparse result", 0.8, "sparse", tenant_id)]

        class MockReranker:
            def rerank(self, query, hits, top_k=8):
                return sorted(hits, key=lambda h: h.score, reverse=True)[:top_k]

        hybrid = HybridRetriever(MockEmbedding(), MockDense(), MockSparse(), MockReranker())
        hits = hybrid.retrieve("t", "test query")
        assert len(hits) == 2
        assert hits[0].score >= hits[1].score


class TestPreciseBlockRetriever:
    def test_filters_summary_nodes(self):
        class DummyHybrid:
            def retrieve(self, tenant_id, query, material_ids=None):
                return [
                    RetrievalHit("s1", "d1", "summary", 0.9, "hybrid", tenant_id, metadata={"node_type": "raptor_summary"}),
                    RetrievalHit("l1", "d1", "leaf", 0.8, "hybrid", tenant_id),
                ]

        hits = PreciseBlockRetriever(DummyHybrid()).retrieve("t", "q")
        assert all(h.metadata.get("node_type") != "raptor_summary" for h in hits)
        assert any(h.chunk_id == "l1" for h in hits)

    def test_returns_empty_on_no_leaf_hits(self):
        class DummyHybrid:
            def retrieve(self, tenant_id, query, material_ids=None):
                return [
                    RetrievalHit("s1", "d1", "summary", 0.9, "hybrid", tenant_id, metadata={"node_type": "raptor_summary"}),
                ]

        hits = PreciseBlockRetriever(DummyHybrid()).retrieve("t", "q")
        assert len(hits) == 0
