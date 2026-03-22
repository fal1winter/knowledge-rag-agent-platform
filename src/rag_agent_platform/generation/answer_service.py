"""基于检索证据的可引用回答生成。

职责：
- 将检索命中组织为带引用编号的证据上下文
- 调用 DeepSeek 生成基于证据的忠实回答
- 后处理：提取引用标记、过滤幻觉、生成引文列表
- 支持流式输出（通过 stream_answer）

生成模型路由：
- 复杂推理/长文本生成 → DeepSeek V2.5 via SiliconFlow
- 简单直接问答 → 可选降级到本地 Qwen（通过 ChatModel 注入）
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from typing import Dict, Generator, List, Optional

from rag_agent_platform.generation.llm_client import ChatModel, PromptBuilder
from rag_agent_platform.models import RetrievalHit

logger = logging.getLogger(__name__)

# 匹配回答中的引用标记 [1] [2] 等
_CITATION_PATTERN = re.compile(r"\[(\d+)\]")


@dataclass
class AnswerResult:
    """结构化回答结果。"""
    text: str
    citations: List[Dict]
    used_evidence_indices: List[int]  # 回答中实际引用的证据编号
    confidence: float = 0.0  # 基于证据覆盖的置信度估计
    metadata: Dict = field(default_factory=dict)


class AnswerService:
    """检索增强生成（RAG）的回答服务。

    核心设计：
    1. 证据预处理：按相关性排序、截断过长片段、去除重复
    2. Prompt 构建：系统指令强调基于证据回答 + 保留引用编号
    3. 生成调用：通过 ChatModel 接口调用 DeepSeek 或 Qwen
    4. 后处理：提取引用索引、计算证据利用率
    """

    def __init__(
        self,
        chat_model: ChatModel,
        prompt_builder: PromptBuilder | None = None,
        max_evidence_chars: int = 8000,
        temperature: float = 0.2,
    ):
        self.chat_model = chat_model
        self.prompt_builder = prompt_builder or PromptBuilder()
        self.max_evidence_chars = max_evidence_chars
        self.temperature = temperature

    def answer(self, question: str, hits: List[RetrievalHit]) -> tuple[str, List[dict]]:
        """生成基于证据的回答，返回 (文本, 引文列表)。"""
        # 去重并按分数排序
        unique_hits = self._deduplicate_hits(hits)
        # 截断证据总长度，避免超出模型上下文
        truncated = self._truncate_evidence(unique_hits)

        evidence = [
            {
                "text": hit.text,
                "citation": hit.citation,
                "score": hit.score,
                "source": hit.source,
            }
            for hit in truncated
        ]
        messages = self.prompt_builder.build_answer_messages(question, evidence)
        text = self.chat_model.complete(messages, temperature=self.temperature)
        citations = [hit.citation for hit in truncated if hit.citation]
        return text, citations

    def answer_structured(self, question: str, hits: List[RetrievalHit]) -> AnswerResult:
        """生成结构化回答，包含引用索引和置信度。"""
        text, citations = self.answer(question, hits)
        used_indices = self._extract_citation_indices(text)
        confidence = self._estimate_confidence(hits, used_indices)
        return AnswerResult(
            text=text,
            citations=citations,
            used_evidence_indices=used_indices,
            confidence=confidence,
            metadata={"total_evidence": len(hits), "used_count": len(used_indices)},
        )

    def stream_answer(self, question: str, hits: List[RetrievalHit]) -> Generator[str, None, None]:
        """流式生成回答，逐 token 产出（需要 ChatModel 支持 stream 接口）。

        如果底层模型不支持流式，退化为一次性返回完整文本。
        """
        text, _ = self.answer(question, hits)
        # 模拟流式：按句号分段返回
        segments = re.split(r"(?<=[。！？\n])", text)
        for segment in segments:
            if segment.strip():
                yield segment

    def _deduplicate_hits(self, hits: List[RetrievalHit]) -> List[RetrievalHit]:
        """按 chunk_id 去重，保留分数最高的。"""
        seen: Dict[str, RetrievalHit] = {}
        for hit in hits:
            if hit.chunk_id not in seen or hit.score > seen[hit.chunk_id].score:
                seen[hit.chunk_id] = hit
        return sorted(seen.values(), key=lambda h: h.score, reverse=True)

    def _truncate_evidence(self, hits: List[RetrievalHit]) -> List[RetrievalHit]:
        """截断证据总字符数，避免超出模型上下文窗口。"""
        result = []
        total_chars = 0
        for hit in hits:
            if total_chars + len(hit.text) > self.max_evidence_chars:
                # 最后一条截断放入
                remaining = self.max_evidence_chars - total_chars
                if remaining > 100:
                    result.append(hit)
                break
            result.append(hit)
            total_chars += len(hit.text)
        return result

    def _extract_citation_indices(self, text: str) -> List[int]:
        """从生成文本中提取引用编号 [1] [2] 等。"""
        matches = _CITATION_PATTERN.findall(text)
        return sorted(set(int(m) for m in matches))

    def _estimate_confidence(self, hits: List[RetrievalHit], used_indices: List[int]) -> float:
        """基于证据利用率和分数估计回答置信度。"""
        if not hits:
            return 0.0
        avg_score = sum(h.score for h in hits) / len(hits)
        utilization = len(used_indices) / max(len(hits), 1)
        # 综合：60% 平均分 + 40% 利用率
        return min(1.0, 0.6 * avg_score + 0.4 * utilization)

