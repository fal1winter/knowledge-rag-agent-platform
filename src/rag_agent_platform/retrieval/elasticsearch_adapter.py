"""Elasticsearch BM25 稀疏检索适配器。"""

import json
from typing import Iterable, List, Optional
import urllib.request

from rag_agent_platform.integrations.http_json import JsonHttpClient
from rag_agent_platform.models import Chunk, RetrievalHit


class ElasticsearchBM25Retriever:
    def __init__(self, url: str, index: str = "knowledge_chunks", fallback=None):
        self.url = url.rstrip('/')
        self.index = index
        self.fallback = fallback
        self.http = JsonHttpClient(timeout=20.0)

    def search(self, tenant_id: str, query: str, top_k: int, material_ids: Optional[List[str]] = None) -> List[RetrievalHit]:
        filters = [{"term": {"tenant_id.keyword": tenant_id}}]
        if material_ids:
            filters.append({"terms": {"document_id.keyword": material_ids}})
        body = {
            "size": top_k,
            "query": {
                "bool": {
                    "must": [{"match": {"text": {"query": query, "operator": "or"}}}],
                    "filter": filters,
                }
            },
            "highlight": {"fields": {"text": {}}},
        }
        try:
            data = self.http.post(f'{self.url}/{self.index}/_search', body)
            hits = data.get('hits', {}).get('hits', [])
            return [self._to_hit(hit) for hit in hits]
        except Exception:
            if self.fallback is not None:
                return self.fallback.search(tenant_id, query, top_k, material_ids)
            raise

    def upsert_documents(self, chunks: Iterable[Chunk]) -> None:
        operations = []
        for chunk in chunks:
            operations.append({"index": {"_index": self.index, "_id": chunk.chunk_id}})
            operations.append({
                "chunk_id": chunk.chunk_id,
                "document_id": chunk.document_id,
                "tenant_id": chunk.tenant_id,
                "text": chunk.text,
                "page": chunk.page,
                "level": chunk.level,
                "metadata": chunk.metadata,
            })
        if not operations:
            return
        body = '\n'.join(json.dumps(item, ensure_ascii=False) for item in operations) + '\n'
        try:
            self._bulk(body)
        except Exception:
            if self.fallback is not None:
                self.fallback.upsert_documents(chunks)
            else:
                raise

    def _bulk(self, ndjson_body: str) -> None:
        request = urllib.request.Request(
            f'{self.url}/_bulk',
            data=ndjson_body.encode('utf-8'),
            headers={'Content-Type': 'application/x-ndjson'},
            method='POST',
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            response.read()

    def _to_hit(self, hit: dict) -> RetrievalHit:
        source = hit.get('_source', {})
        metadata = source.get('metadata') or {}
        return RetrievalHit(
            chunk_id=str(source.get('chunk_id', hit.get('_id'))),
            document_id=str(source.get('document_id')),
            text=str(source.get('text', '')),
            score=float(hit.get('_score', 0.0)),
            source='elasticsearch_bm25',
            tenant_id=str(source.get('tenant_id')),
            citation={"document_id": source.get('document_id'), "chunk_id": source.get('chunk_id'), "page": source.get('page')},
            metadata={**metadata, "level": source.get('level')},
        )
