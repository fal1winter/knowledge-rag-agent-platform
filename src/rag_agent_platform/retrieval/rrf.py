"""倒数排名融合（RRF）算法实现。"""

from collections import defaultdict
from typing import Iterable, List

from rag_agent_platform.models import RetrievalHit


class RRFusion:
    def __init__(self, k: int = 60):
        self.k = k

    def fuse(self, ranked_lists: Iterable[List[RetrievalHit]], top_k: int) -> List[RetrievalHit]:
        scores = defaultdict(float)
        best_hit = {}
        for ranked in ranked_lists:
            for rank, hit in enumerate(ranked, start=1):
                scores[hit.chunk_id] += 1.0 / (self.k + rank)
                if hit.chunk_id not in best_hit or hit.score > best_hit[hit.chunk_id].score:
                    best_hit[hit.chunk_id] = hit
        fused = []
        for chunk_id, score in scores.items():
            hit = best_hit[chunk_id]
            fused.append(
                RetrievalHit(
                    chunk_id=hit.chunk_id,
                    document_id=hit.document_id,
                    text=hit.text,
                    score=score,
                    source=f"rrf({hit.source})",
                    tenant_id=hit.tenant_id,
                    citation=hit.citation,
                    metadata={**hit.metadata, "rrf_score": score},
                )
            )
        return sorted(fused, key=lambda item: item.score, reverse=True)[:top_k]

