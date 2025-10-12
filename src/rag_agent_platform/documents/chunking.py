"""文档语义切片工具。"""

from dataclasses import dataclass
import re
from typing import List

from rag_agent_platform.models import Chunk, ParsedDocument


@dataclass
class ChunkingConfig:
    max_chars: int = 900
    overlap_chars: int = 120
    min_chars: int = 120


class SemanticChunker:
    """段落感知的语义切片器，用于 RAPTOR 层次构建前。"""

    def __init__(self, config: ChunkingConfig | None = None):
        self.config = config or ChunkingConfig()

    def split(self, parsed: ParsedDocument) -> List[Chunk]:
        paragraphs = [p.strip() for p in re.split(r"\n{2,}|(?<=[。！？.!?])\s+", parsed.text) if p.strip()]
        chunks: List[Chunk] = []
        buffer = ""
        chunk_index = 0
        for paragraph in paragraphs:
            candidate = f"{buffer}\n{paragraph}".strip() if buffer else paragraph
            if len(candidate) <= self.config.max_chars:
                buffer = candidate
                continue
            if len(buffer) >= self.config.min_chars:
                chunks.append(self._make_chunk(parsed, chunk_index, buffer))
                chunk_index += 1
                buffer = self._tail(buffer) + "\n" + paragraph
            else:
                for part in self._hard_split(candidate):
                    chunks.append(self._make_chunk(parsed, chunk_index, part))
                    chunk_index += 1
                buffer = ""
        if buffer.strip():
            chunks.append(self._make_chunk(parsed, chunk_index, buffer.strip()))
        return chunks

    def _make_chunk(self, parsed: ParsedDocument, index: int, text: str) -> Chunk:
        return Chunk(
            chunk_id=f"{parsed.asset.document_id}:leaf:{index}",
            document_id=parsed.asset.document_id,
            tenant_id=parsed.asset.tenant_id,
            text=text,
            level=0,
            metadata={"title": parsed.asset.title, **parsed.asset.metadata},
        )

    def _tail(self, text: str) -> str:
        if self.config.overlap_chars <= 0:
            return ""
        return text[-self.config.overlap_chars :]

    def _hard_split(self, text: str) -> List[str]:
        step = max(1, self.config.max_chars - self.config.overlap_chars)
        parts = []
        for start in range(0, len(text), step):
            part = text[start : start + self.config.max_chars].strip()
            if part:
                parts.append(part)
        return parts

