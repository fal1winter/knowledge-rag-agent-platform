"""文档端到端摄入检索索引。"""

from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Protocol

from rag_agent_platform.documents.chunking import SemanticChunker
from rag_agent_platform.documents.loaders import DocumentIngestionService
from rag_agent_platform.models import Chunk, DocumentAsset, ParsedDocument


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
    document_id: str
    tenant_id: str
    parsed_chars: int
    leaf_chunks: int
    raptor_nodes: int
    dense_indexed: bool
    sparse_indexed: bool
    graph_entities: int = 0
    metadata: Dict = field(default_factory=dict)


class KnowledgeIngestionPipeline:
    """解析、切片、构建 RAPTOR 层次、写入检索索引。"""

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
    ):
        self.parser = parser
        self.chunker = chunker
        self.raptor_builder = raptor_builder
        self.raptor_store = raptor_store
        self.dense_index = dense_index
        self.sparse_index = sparse_index
        self.graph_index = graph_index
        self.entity_extractor = entity_extractor

    def ingest(self, asset: DocumentAsset) -> IngestionResult:
        parsed = self.parser.parse(asset)
        leaf_chunks = self.chunker.split(parsed)
        raptor_nodes = self.raptor_builder.build(leaf_chunks)

        self.raptor_store.upsert_many(raptor_nodes)
        self.dense_index.upsert_vectors(raptor_nodes)
        self.sparse_index.upsert_documents(leaf_chunks)

        entities: List[Dict] = []
        if self.graph_index and self.entity_extractor:
            entities = self.entity_extractor.extract(parsed, leaf_chunks)
            self.graph_index.upsert_entities(asset.tenant_id, entities)

        return IngestionResult(
            document_id=asset.document_id,
            tenant_id=asset.tenant_id,
            parsed_chars=len(parsed.text),
            leaf_chunks=len(leaf_chunks),
            raptor_nodes=len(raptor_nodes),
            dense_indexed=True,
            sparse_indexed=True,
            graph_entities=len(entities),
            metadata={"title": asset.title, "file_type": asset.file_type},
        )
