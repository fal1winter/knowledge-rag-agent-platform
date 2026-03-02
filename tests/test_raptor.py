"""RAPTOR 树状索引构建与检索单测。"""

import pytest

from rag_agent_platform.models import Chunk
from rag_agent_platform.raptor.tree_index import (
    ExtractiveSummarizer,
    InMemoryRaptorStore,
    RaptorConfig,
    RaptorTreeBuilder,
)
from rag_agent_platform.raptor.retriever import RaptorRetriever
from rag_agent_platform.storage.in_memory import HashEmbeddingClient, InMemoryDenseBackend


@pytest.fixture
def embeddings():
    return HashEmbeddingClient()


@pytest.fixture
def raptor_store():
    return InMemoryRaptorStore()


class TestExtractiveSummarizer:
    def test_joins_sentences(self):
        summarizer = ExtractiveSummarizer()
        texts = ["第一段内容。第二句话。", "第三段补充说明。"]
        summary = summarizer.summarize(texts, max_words=100)
        assert "第一段" in summary
        assert "第三段" in summary

    def test_respects_max_words(self):
        summarizer = ExtractiveSummarizer()
        long_texts = [" ".join(["word"] * 50) for _ in range(5)]
        summary = summarizer.summarize(long_texts, max_words=20)
        assert len(summary.split()) <= 20

    def test_empty_input(self):
        summarizer = ExtractiveSummarizer()
        assert summarizer.summarize([], max_words=50) == ""


class TestRaptorTreeBuilder:
    def test_builds_hierarchy_from_leaf_chunks(self, embeddings):
        summarizer = ExtractiveSummarizer()
        config = RaptorConfig(branching_factor=3, max_levels=2, min_cluster_size=2)
        builder = RaptorTreeBuilder(embeddings, summarizer, config)

        leaves = [
            Chunk(f"leaf-{i}", "doc-1", f"第 {i} 段关于机器学习的内容描述", "t")
            for i in range(6)
        ]
        all_nodes = builder.build(leaves)

        # 应该生成比叶子更多的节点（叶子 + 至少一层父节点）
        assert len(all_nodes) > len(leaves)
        # 父节点应有 child_ids
        parents = [n for n in all_nodes if n.child_ids]
        assert len(parents) > 0
        # 父节点 level > 0
        assert all(p.level > 0 for p in parents)

    def test_single_chunk_returns_itself(self, embeddings):
        summarizer = ExtractiveSummarizer()
        builder = RaptorTreeBuilder(embeddings, summarizer)
        leaves = [Chunk("only", "doc-1", "唯一一段", "t")]
        all_nodes = builder.build(leaves)
        assert len(all_nodes) == 1
        assert all_nodes[0].chunk_id == "only"

    def test_preserves_tenant_id(self, embeddings):
        summarizer = ExtractiveSummarizer()
        config = RaptorConfig(min_cluster_size=2)
        builder = RaptorTreeBuilder(embeddings, summarizer, config)

        leaves = [
            Chunk(f"l{i}", "d1", f"内容 {i}", "my-tenant")
            for i in range(4)
        ]
        all_nodes = builder.build(leaves)
        assert all(n.tenant_id == "my-tenant" for n in all_nodes)


class TestInMemoryRaptorStore:
    def test_upsert_and_get(self, raptor_store):
        chunk = Chunk("c1", "d1", "text", "t", level=0)
        raptor_store.upsert_many([chunk])
        assert raptor_store.get("c1") is not None
        assert raptor_store.get("c1").text == "text"
        assert raptor_store.get("nonexistent") is None

    def test_children_lookup(self, raptor_store):
        child1 = Chunk("child1", "d1", "c1 text", "t", level=0)
        child2 = Chunk("child2", "d1", "c2 text", "t", level=0)
        parent = Chunk("parent", "d1", "summary", "t", level=1, child_ids=["child1", "child2"])
        raptor_store.upsert_many([child1, child2, parent])

        children = raptor_store.children("parent")
        assert len(children) == 2
        assert {c.chunk_id for c in children} == {"child1", "child2"}


class TestRaptorRetriever:
    def test_coarse_to_fine_retrieval(self, embeddings, raptor_store):
        """验证 RAPTOR 检索器：先粗召回摘要节点再下钻到叶子。"""
        leaf = Chunk("leaf-1", "doc", "叶子节点的详细内容", "t", level=0, vector=embeddings.embed("叶子"))
        parent = Chunk("parent-1", "doc", "摘要：关于叶子的概括", "t", level=1,
                       child_ids=["leaf-1"], vector=embeddings.embed("摘要"))
        raptor_store.upsert_many([leaf, parent])

        class MockDense:
            def search(self, tenant_id, query_vector, top_k, level=None):
                if level and level >= 1:
                    return [RetrievalHit("parent-1", "doc", "摘要", 0.9, "dense", tenant_id, metadata={"level": 1})]
                return [RetrievalHit("leaf-1", "doc", "叶子", 0.8, "dense", tenant_id)]

        from rag_agent_platform.models import RetrievalHit
        retriever = RaptorRetriever(embeddings, MockDense(), raptor_store)
        hits = retriever.retrieve("t", "关于叶子的问题")
        # 应返回下钻后的叶子节点
        assert any(h.chunk_id == "leaf-1" for h in hits)
