"""BGE 重排器集成，支持 HTTP 远程和本地推理两种模式。"""

from typing import List, Protocol

from rag_agent_platform.integrations.http_json import JsonHttpClient
from rag_agent_platform.models import RetrievalHit


class RerankClient(Protocol):
    def score(self, query: str, documents: List[str]) -> List[float]:
        """返回查询-文档对的相关性打分。"""


class HTTPRerankClient:
    def __init__(self, endpoint: str, model: str = "BAAI/bge-reranker-v2-m3", api_key: str | None = None):
        headers = {'Authorization': f'Bearer {api_key}'} if api_key else {}
        self.endpoint = endpoint.rstrip('/')
        self.model = model
        self.http = JsonHttpClient(timeout=60.0, headers=headers)

    def score(self, query: str, documents: List[str]) -> List[float]:
        payload = {"model": self.model, "query": query, "documents": documents}
        data = self.http.post(self.endpoint, payload)
        if "scores" in data:
            return [float(score) for score in data["scores"]]
        results = data.get("results", [])
        return [float(item.get("relevance_score", item.get("score", 0.0))) for item in results]


class LocalBGERerankClient:
    def __init__(self, model: str = "BAAI/bge-reranker-v2-m3"):
        from FlagEmbedding import FlagReranker
        self.reranker = FlagReranker(model, use_fp16=True)

    def score(self, query: str, documents: List[str]) -> List[float]:
        pairs = [[query, doc] for doc in documents]
        scores = self.reranker.compute_score(pairs)
        if isinstance(scores, float):
            return [scores]
        return [float(score) for score in scores]


class BGEReranker:
    def __init__(
        self,
        client: RerankClient | None = None,
        model: str = "BAAI/bge-reranker-v2-m3",
        allow_lexical_fallback: bool = True,
    ):
        self.client = client
        self.model = model
        self.allow_lexical_fallback = allow_lexical_fallback

    def rerank(self, query: str, hits: List[RetrievalHit], top_k: int) -> List[RetrievalHit]:
        if not hits:
            return []
        scored_by_model = False
        if self.client is None:
            scores = self._lexical_scores(query, [hit.text for hit in hits])
        else:
            try:
                scores = self.client.score(query, [hit.text for hit in hits])
                scored_by_model = True
            except Exception:
                if not self.allow_lexical_fallback:
                    raise
                scores = self._lexical_scores(query, [hit.text for hit in hits])
        rescored = []
        for hit, score in zip(hits, scores):
            rescored.append(
                RetrievalHit(
                    chunk_id=hit.chunk_id,
                    document_id=hit.document_id,
                    text=hit.text,
                    score=float(score),
                    source=f"bge_reranker:{hit.source}" if scored_by_model else f"lexical_rerank:{hit.source}",
                    tenant_id=hit.tenant_id,
                    citation=hit.citation,
                    metadata={**hit.metadata, "reranker_model": self.model, "pre_rerank_score": hit.score},
                )
            )
        return sorted(rescored, key=lambda hit: hit.score, reverse=True)[:top_k]

    def _lexical_scores(self, query: str, documents: List[str]) -> List[float]:
        terms = set(query.lower().split())
        scores = []
        for doc in documents:
            doc_terms = set(doc.lower().split())
            scores.append(len(terms & doc_terms) / max(1, len(terms | doc_terms)))
        return scores
