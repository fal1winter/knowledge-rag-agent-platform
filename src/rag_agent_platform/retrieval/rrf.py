"""倒数排名融合（Reciprocal Rank Fusion）实现。

RRF 是一种无参数（仅需常量 k）的排名聚合算法，将多路异构检索结果
融合为统一排序。本实现额外支持：
- 加权 RRF：为不同来源分配权重（如稠密 0.6 + 稀疏 0.4）
- 分数归一化：输出分数映射到 [0, 1] 区间便于下游阈值判断
- 来源溯源：保留每条结果的原始检索来源标记
"""

from collections import defaultdict
from typing import Dict, Iterable, List, Optional

from rag_agent_platform.models import RetrievalHit


class RRFusion:
    """Reciprocal Rank Fusion 融合器。

    算法：score(d) = Σ_i weight_i / (k + rank_i(d))
    其中 k 为平滑常数（默认 60），rank_i(d) 为文档 d 在第 i 路排名列表中的位置。
    k 越大，排名靠后的文档得分衰减越慢，融合结果越平滑。
    """

    def __init__(self, k: int = 60, normalize: bool = True):
        self.k = k
        self.normalize = normalize

    def fuse(
        self,
        ranked_lists: Iterable[List[RetrievalHit]],
        top_k: int,
        weights: Optional[List[float]] = None,
    ) -> List[RetrievalHit]:
        """融合多路排名列表。

        Args:
            ranked_lists: 各路检索结果（已按相关性降序排列）
            top_k: 最终返回的最大文档数
            weights: 各路权重，长度需与 ranked_lists 一致；None 时等权

        Returns:
            融合后按 RRF 分数降序排列的结果列表
        """
        scores: Dict[str, float] = defaultdict(float)
        best_hit: Dict[str, RetrievalHit] = {}
        source_ranks: Dict[str, Dict[str, int]] = defaultdict(dict)  # chunk_id -> {source: rank}

        lists = list(ranked_lists)
        if weights is None:
            weights = [1.0] * len(lists)

        for list_idx, ranked in enumerate(lists):
            w = weights[list_idx] if list_idx < len(weights) else 1.0
            for rank, hit in enumerate(ranked, start=1):
                scores[hit.chunk_id] += w / (self.k + rank)
                if hit.chunk_id not in best_hit or hit.score > best_hit[hit.chunk_id].score:
                    best_hit[hit.chunk_id] = hit
                source_ranks[hit.chunk_id][hit.source] = rank

        # 构建融合结果
        fused: List[RetrievalHit] = []
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
                    metadata={
                        **hit.metadata,
                        "rrf_raw_score": score,
                        "source_ranks": source_ranks[chunk_id],
                    },
                )
            )

        fused.sort(key=lambda item: item.score, reverse=True)
        result = fused[:top_k]

        # 可选归一化到 [0, 1]
        if self.normalize and result:
            max_score = result[0].score
            if max_score > 0:
                for hit in result:
                    hit.score = hit.score / max_score

        return result

