"""面向简单知识库问答的精准块检索。

设计目标：
- 只返回叶子级原文块（过滤 RAPTOR 摘要节点），确保引文可追溯
- 支持上下文窗口扩展：命中块前后相邻块一并返回，提升回答连贯性
- 支持最低分数阈值，过滤低相关性噪声
- 输出结果附带段落位置标记，便于前端高亮定位
"""

from dataclasses import dataclass
from typing import Dict, List, Optional

from rag_agent_platform.models import RetrievalHit
from rag_agent_platform.retrieval.hybrid import HybridRetriever


@dataclass
class PreciseBlockConfig:
    """精准块检索配置。"""
    candidate_top_k: int = 8         # 从混合检索获取的候选数量
    final_top_k: int = 4             # 最终返回给生成模块的块数
    min_score: float | None = None   # 最低分数阈值，None 表示不过滤
    context_window: int = 0          # 上下文窗口：向前后各扩展 N 个相邻块
    deduplicate_overlap: bool = True  # 扩展窗口后去重重叠块


class PreciseBlockRetriever:
    """为直接问答返回紧凑的叶子级原文块。

    检索流程：
    1. 调用 HybridRetriever 获取候选（已经过 RRF 融合 + BGE 精排）
    2. 过滤 RAPTOR 摘要节点，只保留叶子级原文
    3. 按分数阈值过滤低相关噪声
    4. 可选：上下文窗口扩展（将命中块的前后邻居一并纳入）
    5. 截断到 final_top_k 返回
    """

    def __init__(self, hybrid_retriever: HybridRetriever, config: PreciseBlockConfig | None = None):
        self.hybrid_retriever = hybrid_retriever
        self.config = config or PreciseBlockConfig()

    def retrieve(
        self,
        tenant_id: str,
        query: str,
        material_ids: Optional[List[str]] = None,
    ) -> List[RetrievalHit]:
        hits = self.hybrid_retriever.retrieve(tenant_id, query, material_ids)

        # 过滤 RAPTOR 摘要节点，只保留叶子原文块
        leaf_hits = [
            hit
            for hit in hits[: self.config.candidate_top_k]
            if hit.metadata.get("node_type") != "raptor_summary"
        ]

        # 分数阈值过滤
        if self.config.min_score is not None:
            leaf_hits = [hit for hit in leaf_hits if hit.score >= self.config.min_score]

        # 上下文窗口扩展
        if self.config.context_window > 0:
            leaf_hits = self._expand_context_window(leaf_hits)

        result = leaf_hits[: self.config.final_top_k]

        # 附加段落位置元数据
        for idx, hit in enumerate(result):
            hit.metadata["precise_rank"] = idx + 1

        return result

    def _expand_context_window(self, hits: List[RetrievalHit]) -> List[RetrievalHit]:
        """扩展上下文窗口：按 document_id 分组，将命中块的相邻块纳入结果。

        通过 chunk_id 中的序号推断相邻关系（格式：{doc_id}:chunk:{seq}）。
        无法推断时保持原样。
        """
        if not hits:
            return hits

        expanded: Dict[str, RetrievalHit] = {}
        for hit in hits:
            expanded[hit.chunk_id] = hit
            # 尝试解析序号扩展相邻块（依赖 chunk_id 命名约定）
            parts = hit.chunk_id.rsplit(":", 1)
            if len(parts) == 2 and parts[1].isdigit():
                prefix, seq = parts[0], int(parts[1])
                for offset in range(-self.config.context_window, self.config.context_window + 1):
                    if offset == 0:
                        continue
                    neighbor_id = f"{prefix}:{seq + offset}"
                    if neighbor_id not in expanded:
                        # 相邻块作为低分上下文标记，不影响排序
                        expanded[neighbor_id] = RetrievalHit(
                            chunk_id=neighbor_id,
                            document_id=hit.document_id,
                            text="",  # 实际文本需从索引补充加载
                            score=hit.score * 0.5,  # 降权标记
                            source="context_window",
                            tenant_id=hit.tenant_id,
                            metadata={"context_of": hit.chunk_id, "node_type": "context_expansion"},
                        )

        # 按原始分数降序重排
        result = sorted(expanded.values(), key=lambda h: h.score, reverse=True)
        if self.config.deduplicate_overlap:
            seen = set()
            deduped = []
            for h in result:
                if h.chunk_id not in seen:
                    seen.add(h.chunk_id)
                    deduped.append(h)
            return deduped
        return result
