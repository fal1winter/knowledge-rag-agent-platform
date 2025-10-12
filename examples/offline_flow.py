"""Offline walkthrough showing the project chain without external services."""

from rag_agent_platform.bootstrap import build_agent
from rag_agent_platform.documents.chunking import SemanticChunker
from rag_agent_platform.models import DocumentAsset, ParsedDocument, QueryRequest, RetrievalHit
from rag_agent_platform.raptor.tree_index import ExtractiveSummarizer, InMemoryRaptorStore, RaptorTreeBuilder
from rag_agent_platform.storage.in_memory import HashEmbeddingClient, InMemoryDenseBackend, InMemorySparseBackend


def main() -> None:
    asset = DocumentAsset(
        document_id="demo-doc",
        uri="memory://demo",
        file_type="txt",
        tenant_id="tenant-a",
        title="RAG 平台设计",
    )
    parsed = ParsedDocument(
        asset=asset,
        text=(
            "RAPTOR 使用树状层级索引解决长文档跨章节召回问题。

"
            "混合检索融合 Milvus 稠密向量和 Elasticsearch BM25，再用 BGE reranker 精排。

"
            "复杂问题通过 agentic 迭代检索补齐证据。"
        ),
    )
    chunks = SemanticChunker().split(parsed)
    embeddings = HashEmbeddingClient()
    tree_nodes = RaptorTreeBuilder(embeddings, ExtractiveSummarizer()).build(chunks)
    tree_store = InMemoryRaptorStore()
    tree_store.upsert_many(tree_nodes)

    dense = InMemoryDenseBackend()
    sparse = InMemorySparseBackend()
    hits = [
        RetrievalHit(
            chunk_id=node.chunk_id,
            document_id=node.document_id,
            text=node.text,
            score=1.0,
            source="offline_memory",
            tenant_id=node.tenant_id,
            citation={"document_id": node.document_id, "chunk_id": node.chunk_id},
            metadata={**node.metadata, "level": node.level},
        )
        for node in tree_nodes
    ]
    dense.add_hits(hits)
    sparse.add_hits(hits)

    agent = build_agent(embeddings=embeddings, dense=dense, sparse=sparse, tree_store=tree_store)
    answer = agent.handle(
        QueryRequest(
            tenant_id="tenant-a",
            user_id="user-a",
            message="/agentic 比较 RAPTOR 和混合检索分别解决什么问题",
        )
    )
    print(answer.route)
    print(answer.text)


if __name__ == "__main__":
    main()
