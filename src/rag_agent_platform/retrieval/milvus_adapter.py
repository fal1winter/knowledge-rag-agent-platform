"""Milvus 稠密向量检索适配器。"""

from typing import Iterable, List

from rag_agent_platform.models import Chunk, RetrievalHit


class MilvusDenseRetriever:
    def __init__(self, uri: str, collection: str = "knowledge_chunks", fallback=None):
        self.uri = uri
        self.collection = collection
        self.fallback = fallback
        self._client = None

    def _milvus(self):
        if self._client is None:
            from pymilvus import MilvusClient
            self._client = MilvusClient(uri=self.uri)
        return self._client

    def search(self, tenant_id: str, query_vector: List[float], top_k: int, level: int | None = None) -> List[RetrievalHit]:
        if not query_vector:
            return []
        try:
            expr = f'tenant_id == "{tenant_id}"'
            if level is not None:
                expr += f' and level == {int(level)}'
            rows = self._milvus().search(
                collection_name=self.collection,
                data=[query_vector],
                limit=top_k,
                filter=expr,
                output_fields=["chunk_id", "document_id", "tenant_id", "text", "page", "level", "metadata"],
            )
            return [self._to_hit(row) for row in (rows[0] if rows else [])]
        except Exception:
            if self.fallback is not None:
                return self.fallback.search(tenant_id, query_vector, top_k, level)
            raise

    def upsert_vectors(self, chunks: Iterable[Chunk]) -> None:
        rows = []
        for chunk in chunks:
            if chunk.vector is None:
                continue
            rows.append({
                "chunk_id": chunk.chunk_id,
                "document_id": chunk.document_id,
                "tenant_id": chunk.tenant_id,
                "text": chunk.text,
                "page": chunk.page,
                "level": chunk.level,
                "metadata": chunk.metadata,
                "vector": chunk.vector,
            })
        if not rows:
            return
        try:
            self._milvus().upsert(collection_name=self.collection, data=rows)
        except Exception:
            if self.fallback is not None:
                self.fallback.upsert_vectors(chunks)
            else:
                raise

    def _to_hit(self, row) -> RetrievalHit:
        entity = row.get("entity", row)
        metadata = entity.get("metadata") or {}
        return RetrievalHit(
            chunk_id=str(entity.get("chunk_id")),
            document_id=str(entity.get("document_id")),
            text=str(entity.get("text", "")),
            score=float(row.get("distance", row.get("score", 0.0))),
            source="milvus_dense",
            tenant_id=str(entity.get("tenant_id")),
            citation={"document_id": entity.get("document_id"), "chunk_id": entity.get("chunk_id"), "page": entity.get("page")},
            metadata={**metadata, "level": entity.get("level")},
        )
