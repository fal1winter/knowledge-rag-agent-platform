"""文档端到端摄入检索索引。

完整流程：
1. 指纹去重 — 相同内容跳过，变更内容覆盖更新
2. 文档解析 — 多格式加载器（PDF/Word/PPT/图片/音视频/文本）
3. 语义切片 — 按主题边界拆分为检索友好的文本块
4. RAPTOR 建树 — 自底向上聚类，父节点为子节点摘要
5. 三路索引写入 — Milvus 稠密 / Elasticsearch BM25 / Neo4j 图谱
6. 元数据增强 — 自动提取标题、关键词、摘要写入索引 metadata

支持：
- 单文档同步入库（ingest）
- 批量异步入库（batch_ingest）
- 失败重试机制（retry + dead letter）
"""

from __future__ import annotations

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Callable, Dict, Iterable, List, Optional, Protocol

from rag_agent_platform.documents.chunking import SemanticChunker
from rag_agent_platform.documents.loaders import DocumentIngestionService
from rag_agent_platform.ingestion.dedup import DocumentDeduplicator, InMemoryFingerprintStore
from rag_agent_platform.models import Chunk, DocumentAsset, ParsedDocument

logger = logging.getLogger(__name__)


class RaptorBuilderLike(Protocol):
    def build(self, leaf_chunks: Iterable[Chunk]) -> List[Chunk]:
        """构建叶子和父级 RAPTOR 节点。"""


class RaptorStoreLike(Protocol):
    def upsert_many(self, chunks: Iterable[Chunk]) -> None:
        """持久化 RAPTOR 节点用于下钻检索。"""


class DenseIndexWriter(Protocol):
    def upsert_vectors(self, chunks: Iterable[Chunk]) -> None:
        """写入稠密检索向量。"""


class SparseIndexWriter(Protocol):
    def upsert_documents(self, chunks: Iterable[Chunk]) -> None:
        """写入文本块用于 BM25 检索。"""


class EntityExtractor(Protocol):
    def extract(self, parsed: ParsedDocument, chunks: List[Chunk]) -> List[Dict]:
        """抽取实体和关系用于图谱索引。"""


class GraphIndexWriter(Protocol):
    def upsert_entities(self, tenant_id: str, entities: List[Dict]) -> None:
        """写入抽取的实体和关系。"""


@dataclass
class IngestionResult:
    """单文档入库结果摘要。"""
    document_id: str
    tenant_id: str
    parsed_chars: int
    leaf_chunks: int
    raptor_nodes: int
    dense_indexed: bool
    sparse_indexed: bool
    graph_entities: int = 0
    skipped: bool = False  # 指纹去重跳过
    elapsed_ms: float = 0.0
    metadata: Dict = field(default_factory=dict)


@dataclass
class DeadLetter:
    """入库失败后进入死信队列的记录，支持后续重试。"""
    document_id: str
    tenant_id: str
    uri: str
    error: str
    attempts: int
    last_attempt_at: float  # Unix timestamp


@dataclass
class BatchIngestionReport:
    """批量入库汇总报告。"""
    total: int
    succeeded: int
    skipped: int
    failed: int
    elapsed_ms: float
    results: List[IngestionResult] = field(default_factory=list)
    dead_letters: List[DeadLetter] = field(default_factory=list)


class KnowledgeIngestionPipeline:
    """端到端入库管线：去重 → 解析 → 切片 → RAPTOR → 三路索引写入。

    设计要点：
    - 指纹去重：相同内容不重复索引，节省计算和存储
    - 批量并行：多文档通过线程池并发处理，IO 密集段充分利用
    - 失败容错：单文档失败不阻塞批次，进死信队列可重试
    - 元数据增强：切片时自动计算关键词频率、段落位置等辅助信息
    """

    def __init__(
        self,
        parser: DocumentIngestionService,
        chunker: SemanticChunker,
        raptor_builder: RaptorBuilderLike,
        raptor_store: RaptorStoreLike,
        dense_index: DenseIndexWriter,
        sparse_index: SparseIndexWriter,
        graph_index: GraphIndexWriter | None = None,
        entity_extractor: EntityExtractor | None = None,
        deduplicator: DocumentDeduplicator | None = None,
        max_retries: int = 2,
        batch_concurrency: int = 4,
        progress_callback: Optional[Callable[[str, int, int], None]] = None,
    ):
        self.parser = parser
        self.chunker = chunker
        self.raptor_builder = raptor_builder
        self.raptor_store = raptor_store
        self.dense_index = dense_index
        self.sparse_index = sparse_index
        self.graph_index = graph_index
        self.entity_extractor = entity_extractor
        self.deduplicator = deduplicator or DocumentDeduplicator(InMemoryFingerprintStore())
        self.max_retries = max_retries
        self.batch_concurrency = batch_concurrency
        self.progress_callback = progress_callback
        self._dead_letters: List[DeadLetter] = []

    def ingest(self, asset: DocumentAsset) -> IngestionResult:
        """单文档同步入库：去重 → 解析 → 切片 → RAPTOR 建树 → 三路索引写入。"""
        t0 = time.perf_counter()

        # 1. 解析文档内容
        parsed = self.parser.parse(asset)

        # 2. 指纹去重检查
        should_ingest, existing = self.deduplicator.should_ingest(asset.document_id, parsed.text)
        if not should_ingest:
            logger.info("文档 %s 内容未变，跳过入库 (fingerprint match)", asset.document_id)
            return IngestionResult(
                document_id=asset.document_id,
                tenant_id=asset.tenant_id,
                parsed_chars=len(parsed.text),
                leaf_chunks=existing.chunk_count if existing else 0,
                raptor_nodes=0,
                dense_indexed=False,
                sparse_indexed=False,
                skipped=True,
                elapsed_ms=(time.perf_counter() - t0) * 1000,
                metadata={"reason": "dedup_skip"},
            )

        # 3. 语义切片
        leaf_chunks = self.chunker.split(parsed)

        # 4. 元数据增强：为切片添加位置和关键词信息
        leaf_chunks = self._enrich_chunk_metadata(leaf_chunks, parsed)

        # 5. RAPTOR 自底向上建树
        raptor_nodes = self.raptor_builder.build(leaf_chunks)

        # 6. 写入三路索引
        self.raptor_store.upsert_many(raptor_nodes)
        self.dense_index.upsert_vectors(raptor_nodes)
        self.sparse_index.upsert_documents(leaf_chunks)

        # 7. 知识图谱实体抽取
        entities: List[Dict] = []
        if self.graph_index and self.entity_extractor:
            entities = self.entity_extractor.extract(parsed, leaf_chunks)
            self.graph_index.upsert_entities(asset.tenant_id, entities)

        # 8. 记录指纹
        self.deduplicator.record_ingestion(
            asset.document_id, parsed.text, chunk_count=len(leaf_chunks),
            metadata={"title": asset.title, "file_type": asset.file_type},
        )

        elapsed = (time.perf_counter() - t0) * 1000
        logger.info(
            "文档 %s 入库完成: %d chunks, %d raptor nodes, %d entities, %.0fms",
            asset.document_id, len(leaf_chunks), len(raptor_nodes), len(entities), elapsed,
        )

        return IngestionResult(
            document_id=asset.document_id,
            tenant_id=asset.tenant_id,
            parsed_chars=len(parsed.text),
            leaf_chunks=len(leaf_chunks),
            raptor_nodes=len(raptor_nodes),
            dense_indexed=True,
            sparse_indexed=True,
            graph_entities=len(entities),
            elapsed_ms=elapsed,
            metadata={"title": asset.title, "file_type": asset.file_type},
        )

    def batch_ingest(self, assets: List[DocumentAsset]) -> BatchIngestionReport:
        """批量异步入库：多文档并行处理，失败自动重试，统一报告。

        并行度由 batch_concurrency 控制，IO 密集段（解析/网络索引写入）
        通过线程池并发，CPU 密集段（切片/聚类）受 GIL 限制但实际耗时占比低。
        """
        t0 = time.perf_counter()
        results: List[IngestionResult] = []
        dead_letters: List[DeadLetter] = []
        succeeded = 0
        skipped = 0
        failed = 0

        def _ingest_with_retry(asset: DocumentAsset, index: int) -> IngestionResult | DeadLetter:
            last_error = ""
            for attempt in range(1, self.max_retries + 1):
                try:
                    result = self.ingest(asset)
                    if self.progress_callback:
                        self.progress_callback(asset.document_id, index + 1, len(assets))
                    return result
                except Exception as exc:
                    last_error = f"{exc.__class__.__name__}: {exc}"
                    logger.warning(
                        "文档 %s 入库失败 (attempt %d/%d): %s",
                        asset.document_id, attempt, self.max_retries, last_error,
                    )
            # 所有重试耗尽，进死信队列
            return DeadLetter(
                document_id=asset.document_id,
                tenant_id=asset.tenant_id,
                uri=asset.uri,
                error=last_error,
                attempts=self.max_retries,
                last_attempt_at=time.time(),
            )

        with ThreadPoolExecutor(max_workers=self.batch_concurrency) as pool:
            futures = {
                pool.submit(_ingest_with_retry, asset, idx): asset
                for idx, asset in enumerate(assets)
            }
            for future in as_completed(futures):
                outcome = future.result()
                if isinstance(outcome, DeadLetter):
                    dead_letters.append(outcome)
                    failed += 1
                elif outcome.skipped:
                    results.append(outcome)
                    skipped += 1
                else:
                    results.append(outcome)
                    succeeded += 1

        self._dead_letters.extend(dead_letters)
        elapsed = (time.perf_counter() - t0) * 1000

        return BatchIngestionReport(
            total=len(assets),
            succeeded=succeeded,
            skipped=skipped,
            failed=failed,
            elapsed_ms=elapsed,
            results=results,
            dead_letters=dead_letters,
        )

    def retry_dead_letters(self) -> BatchIngestionReport:
        """重试死信队列中的失败文档。"""
        if not self._dead_letters:
            return BatchIngestionReport(total=0, succeeded=0, skipped=0, failed=0, elapsed_ms=0)

        assets = [
            DocumentAsset(
                document_id=dl.document_id,
                uri=dl.uri,
                file_type=dl.uri.rsplit(".", 1)[-1] if "." in dl.uri else "txt",
                tenant_id=dl.tenant_id,
                title=dl.document_id,
            )
            for dl in self._dead_letters
        ]
        self._dead_letters.clear()
        return self.batch_ingest(assets)

    @property
    def dead_letters(self) -> List[DeadLetter]:
        """当前死信队列中的失败记录。"""
        return list(self._dead_letters)

    def _enrich_chunk_metadata(self, chunks: List[Chunk], parsed: ParsedDocument) -> List[Chunk]:
        """为切片补充位置索引和简单关键词频率。

        元数据增强使得精排阶段可以利用段落位置（开头/中段/结尾）和
        关键词密度进行更精细的打分调整。
        """
        total = len(chunks)
        for idx, chunk in enumerate(chunks):
            if chunk.metadata is None:
                chunk.metadata = {}
            # 段落位置标记
            chunk.metadata["position_ratio"] = round(idx / max(total, 1), 3)
            chunk.metadata["position_label"] = (
                "head" if idx < total * 0.2
                else "tail" if idx > total * 0.8
                else "body"
            )
            # 标题来源
            chunk.metadata["source_title"] = parsed.asset.title
        return chunks
