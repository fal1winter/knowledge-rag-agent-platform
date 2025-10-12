"""内存适配器，用于离线演示和单元测试。"""

import hashlib
import math
import re
from collections import Counter, defaultdict
from typing import Iterable, List

from rag_agent_platform.models import RetrievalHit


class HashEmbeddingClient:
    """确定性本地向量降级，用于测试和演示。"""

    def embed(self, text: str) -> List[float]:
        tokens = re.findall(r"[一-龥A-Za-z0-9_]+", text.lower()) or [text]
        vector = [0.0] * 256
        for token in tokens:
            digest = hashlib.sha256(token.encode('utf-8')).digest()
            for idx, byte in enumerate(digest):
                vector[idx % len(vector)] += (byte / 255.0) * 2.0 - 1.0
        norm = sum(v * v for v in vector) ** 0.5
        return [v / norm for v in vector] if norm else vector


class InMemoryDenseBackend:
    def __init__(self):
        self.hits: List[RetrievalHit] = []

    def add_hits(self, hits: List[RetrievalHit]) -> None:
        self.hits.extend(hits)

    def search(self, tenant_id: str, query_vector: List[float], top_k: int, level: int | None = None) -> List[RetrievalHit]:
        candidates = [hit for hit in self.hits if hit.tenant_id == tenant_id]
        if level is not None:
            candidates = [hit for hit in candidates if hit.metadata.get('level', 0) == level]
        rescored = []
        for hit in candidates:
            vector = hit.metadata.get('vector') or []
            score = self._cosine(query_vector, vector) if vector else hit.score
            rescored.append(self._copy_with_score(hit, score))
        return sorted(rescored, key=lambda hit: hit.score, reverse=True)[:top_k]

    def upsert_vectors(self, chunks: Iterable) -> None:
        for chunk in chunks:
            self.hits.append(
                RetrievalHit(
                    chunk_id=chunk.chunk_id,
                    document_id=chunk.document_id,
                    text=chunk.text,
                    score=1.0,
                    source='in_memory_dense',
                    tenant_id=chunk.tenant_id,
                    citation={'document_id': chunk.document_id, 'chunk_id': chunk.chunk_id, 'page': chunk.page},
                    metadata={**chunk.metadata, 'level': chunk.level, 'vector': chunk.vector or []},
                )
            )

    def _cosine(self, left: List[float], right: List[float]) -> float:
        if not left or not right:
            return 0.0
        dot = sum(a * b for a, b in zip(left, right))
        return dot / max(1e-9, (sum(a * a for a in left) ** 0.5) * (sum(b * b for b in right) ** 0.5))

    def _copy_with_score(self, hit: RetrievalHit, score: float) -> RetrievalHit:
        return RetrievalHit(hit.chunk_id, hit.document_id, hit.text, score, hit.source, hit.tenant_id, hit.citation, hit.metadata)


class InMemorySparseBackend:
    def __init__(self):
        self.hits: List[RetrievalHit] = []

    def add_hits(self, hits: List[RetrievalHit]) -> None:
        self.hits.extend(hits)

    def search(self, tenant_id: str, query: str, top_k: int, material_ids=None) -> List[RetrievalHit]:
        docs = [hit for hit in self.hits if hit.tenant_id == tenant_id and (not material_ids or hit.document_id in material_ids)]
        query_terms = self._tokens(query)
        doc_freq = defaultdict(int)
        tokenized = []
        for hit in docs:
            terms = self._tokens(hit.text)
            tokenized.append((hit, terms))
            for term in set(terms):
                doc_freq[term] += 1
        avg_len = sum(len(terms) for _, terms in tokenized) / max(1, len(tokenized))
        scored = []
        for hit, terms in tokenized:
            score = self._bm25(query_terms, terms, doc_freq, len(tokenized), avg_len)
            scored.append((score, hit))
        return [self._copy_with_score(hit, score) for score, hit in sorted(scored, key=lambda item: item[0], reverse=True)[:top_k]]

    def upsert_documents(self, chunks: Iterable) -> None:
        for chunk in chunks:
            self.hits.append(
                RetrievalHit(
                    chunk_id=chunk.chunk_id,
                    document_id=chunk.document_id,
                    text=chunk.text,
                    score=1.0,
                    source='in_memory_bm25',
                    tenant_id=chunk.tenant_id,
                    citation={'document_id': chunk.document_id, 'chunk_id': chunk.chunk_id, 'page': chunk.page},
                    metadata={**chunk.metadata, 'level': chunk.level},
                )
            )

    def _tokens(self, text: str) -> List[str]:
        return re.findall(r"[一-龥A-Za-z0-9_]+", text.lower())

    def _bm25(self, query_terms: List[str], doc_terms: List[str], doc_freq, doc_count: int, avg_len: float) -> float:
        if not query_terms or not doc_terms:
            return 0.0
        tf = Counter(doc_terms)
        k1 = 1.5
        b = 0.75
        score = 0.0
        for term in query_terms:
            df = doc_freq.get(term, 0)
            if df == 0:
                continue
            idf = math.log(1 + (doc_count - df + 0.5) / (df + 0.5))
            freq = tf[term]
            denom = freq + k1 * (1 - b + b * len(doc_terms) / max(1e-9, avg_len))
            score += idf * (freq * (k1 + 1) / denom)
        return score

    def _copy_with_score(self, hit: RetrievalHit, score: float) -> RetrievalHit:
        return RetrievalHit(hit.chunk_id, hit.document_id, hit.text, score, hit.source, hit.tenant_id, hit.citation, hit.metadata)
