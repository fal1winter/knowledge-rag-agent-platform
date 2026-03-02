"""入库流水线单元测试。"""

import pytest
from pathlib import Path

from rag_agent_platform.models import Chunk, DocumentAsset, ParsedDocument
from rag_agent_platform.documents.chunking import SemanticChunker
from rag_agent_platform.documents.loaders import PlainTextLoader


class TestPlainTextLoader:
    def test_loads_txt_content(self, tmp_path):
        test_file = tmp_path / "sample.txt"
        test_file.write_text("第一行\n第二行\n第三行", encoding="utf-8")
        loader = PlainTextLoader()
        asset = DocumentAsset(
            document_id="doc-1",
            uri=str(test_file),
            file_type="txt",
            tenant_id="t",
            title="sample",
        )
        result = loader.load(asset)
        assert isinstance(result, ParsedDocument)
        assert "第一行" in result.text
        assert "第三行" in result.text

    def test_handles_empty_file(self, tmp_path):
        test_file = tmp_path / "empty.txt"
        test_file.write_text("", encoding="utf-8")
        loader = PlainTextLoader()
        asset = DocumentAsset(
            document_id="doc-2",
            uri=str(test_file),
            file_type="txt",
            tenant_id="t",
            title="empty",
        )
        result = loader.load(asset)
        assert result.text == ""


class TestSemanticChunker:
    @pytest.fixture
    def chunker(self):
        return SemanticChunker()

    def test_splits_long_text(self, chunker):
        paragraphs = [f"这是第 {i} 段内容，包含一些关于机器学习的描述。" * 10 for i in range(10)]
        long_text = "\n\n".join(paragraphs)
        parsed = ParsedDocument(
            asset=DocumentAsset("doc-1", "/fake/path.txt", "txt", "t", "test"),
            text=long_text,
        )
        chunks = chunker.split(parsed)
        assert len(chunks) > 1
        assert all(isinstance(c, Chunk) for c in chunks)
        assert all(c.document_id == "doc-1" for c in chunks)
        assert all(c.tenant_id == "t" for c in chunks)

    def test_short_text_single_chunk(self, chunker):
        short_text = "这是一段很短的文本。"
        parsed = ParsedDocument(
            asset=DocumentAsset("doc-2", "/fake/path.txt", "txt", "t", "test"),
            text=short_text,
        )
        chunks = chunker.split(parsed)
        assert len(chunks) >= 1
        assert chunks[0].text.strip() == short_text

    def test_empty_text_returns_empty(self, chunker):
        parsed = ParsedDocument(
            asset=DocumentAsset("doc-3", "/fake/path.txt", "txt", "t", "test"),
            text="",
        )
        chunks = chunker.split(parsed)
        assert len(chunks) == 0

    def test_preserves_document_metadata(self, chunker):
        parsed = ParsedDocument(
            asset=DocumentAsset("doc-4", "/fake/path.txt", "txt", "tenant-x", "test doc"),
            text="一段足够长的文本内容，用于确保生成至少一个切片。" * 5,
        )
        chunks = chunker.split(parsed)
        assert all(c.document_id == "doc-4" for c in chunks)
        assert all(c.tenant_id == "tenant-x" for c in chunks)


class TestIngestionPipeline:
    def test_end_to_end_ingest(self, tmp_path):
        """验证入库管线从文件到切片的基本流程。"""
        from rag_agent_platform.storage.in_memory import InMemoryDenseBackend, InMemorySparseBackend, HashEmbeddingClient
        from rag_agent_platform.raptor.tree_index import InMemoryRaptorStore, ExtractiveSummarizer, RaptorTreeBuilder
        from rag_agent_platform.ingestion.pipeline import KnowledgeIngestionPipeline
        from rag_agent_platform.documents.loaders import DocumentIngestionService, PlainTextLoader
        from rag_agent_platform.documents.chunking import SemanticChunker
        from rag_agent_platform.retrieval.neo4j_adapter import LocalGraphRetriever
        from rag_agent_platform.ingestion.entity_extractor import HeuristicEntityExtractor

        # 准备测试文件
        test_file = tmp_path / "test.txt"
        test_file.write_text("RAPTOR 是一种树状检索方法。\n\n它通过自底向上聚类构建摘要树。\n\n每个父节点是子节点的摘要。" * 3, encoding="utf-8")

        embeddings = HashEmbeddingClient()
        dense = InMemoryDenseBackend()
        sparse = InMemorySparseBackend()
        tree_store = InMemoryRaptorStore()
        graph = LocalGraphRetriever()

        pipeline = KnowledgeIngestionPipeline(
            parser=DocumentIngestionService([PlainTextLoader()]),
            chunker=SemanticChunker(),
            raptor_builder=RaptorTreeBuilder(embeddings, ExtractiveSummarizer()),
            raptor_store=tree_store,
            dense_index=dense,
            sparse_index=sparse,
            graph_index=graph,
            entity_extractor=HeuristicEntityExtractor(),
        )

        asset = DocumentAsset(
            document_id="doc-ingest-1",
            uri=str(test_file),
            file_type="txt",
            tenant_id="t",
            title="test doc",
        )
        pipeline.ingest(asset)

        # 验证已写入索引
        assert len(tree_store.nodes) > 0
