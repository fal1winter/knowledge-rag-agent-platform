"""知识图谱摄入的实体与关系抽取。

两层设计，与平台整体架构一致：

- ``LLMEntityExtractor`` 通过兼容 OpenAI 格式的模型输出 JSON 格式的实体和
  类型化关系。配置了模型端点时走此生产路径。
- ``HeuristicEntityExtractor`` 为无依赖降级方案。基于切片内共现的名词型 token
  建立相邻关键词链接，使 Neo4j 图谱即使无 LLM 可用也能接收到粗粒度的实体/关系。

两者均返回 ``GraphIndexWriter.upsert_entities`` 所需的结构：

    [{"name": str, "properties": {...}, "relations": [{"type": str, "target": str, "properties": {...}}]}]
"""

from __future__ import annotations

import json
import re
from collections import Counter
from typing import Dict, List

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient
from rag_agent_platform.models import Chunk, ParsedDocument


_TOKEN_RE = re.compile(r"[一-龥A-Za-z][一-龥A-Za-z0-9_]{1,}")
_STOPWORDS = {
    "我们", "可以", "通过", "进行", "以及", "用于", "如下", "需要", "提供", "支持",
    "the", "and", "for", "with", "that", "this", "from", "are", "use", "used",
}


class HeuristicEntityExtractor:
    """基于共现的实体/关系挖掘，无需 LLM。"""

    def __init__(self, max_entities_per_doc: int = 24, relation_type: str = "CO_OCCURS"):
        self.max_entities_per_doc = max_entities_per_doc
        self.relation_type = relation_type

    def extract(self, parsed: ParsedDocument, chunks: List[Chunk]) -> List[Dict]:
        salient = self._salient_terms(chunks)
        if not salient:
            return []
        salient_set = set(salient)
        relations: Dict[str, Counter] = {term: Counter() for term in salient}
        for chunk in chunks:
            present = [term for term in self._tokens(chunk.text) if term in salient_set]
            unique_present = list(dict.fromkeys(present))
            for i, source in enumerate(unique_present):
                for target in unique_present[i + 1 : i + 4]:
                    if source != target:
                        relations[source][target] += 1
        entities: List[Dict] = []
        for term in salient:
            targets = relations[term].most_common(3)
            entities.append(
                {
                    "name": term,
                    "properties": {"document_id": parsed.asset.document_id, "tenant_id": parsed.asset.tenant_id},
                    "relations": [
                        {"type": self.relation_type, "target": target, "properties": {"weight": int(weight)}}
                        for target, weight in targets
                    ],
                }
            )
        return entities

    def _salient_terms(self, chunks: List[Chunk]) -> List[str]:
        counter: Counter = Counter()
        for chunk in chunks:
            counter.update(set(self._tokens(chunk.text)))
        ranked = [term for term, _ in counter.most_common(self.max_entities_per_doc)]
        return ranked

    def _tokens(self, text: str) -> List[str]:
        tokens = []
        for match in _TOKEN_RE.findall(text):
            token = match.strip()
            if len(token) < 2 or token.lower() in _STOPWORDS:
                continue
            tokens.append(token)
        return tokens


class LLMEntityExtractor:
    """调用兼容 OpenAI 格式的模型抽取实体和类型化关系。"""

    def __init__(
        self,
        endpoint: str,
        model: str = "Qwen2.5-7B-Instruct",
        api_key: str | None = None,
        fallback: HeuristicEntityExtractor | None = None,
        max_chars: int = 6000,
    ):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key, timeout=60.0) if endpoint else None
        self.fallback = fallback or HeuristicEntityExtractor()
        self.max_chars = max_chars

    def extract(self, parsed: ParsedDocument, chunks: List[Chunk]) -> List[Dict]:
        if self.client is None:
            return self.fallback.extract(parsed, chunks)
        text = parsed.text[: self.max_chars]
        messages = [
            {
                "role": "system",
                "content": (
                    "Extract a knowledge graph from the document. Return JSON "
                    '{"entities":[{"name":...,"type":...,"relations":[{"type":...,"target":...}]}]}. '
                    "Use concise canonical entity names."
                ),
            },
            {"role": "user", "content": text},
        ]
        try:
            raw = self.client.chat(self.model, messages, temperature=0.0, extra={"response_format": {"type": "json_object"}})
            data = json.loads(raw)
        except Exception:
            return self.fallback.extract(parsed, chunks)
        entities = []
        for item in data.get("entities", []):
            name = str(item.get("name", "")).strip()
            if not name:
                continue
            entities.append(
                {
                    "name": name,
                    "properties": {
                        "type": item.get("type", ""),
                        "document_id": parsed.asset.document_id,
                        "tenant_id": parsed.asset.tenant_id,
                    },
                    "relations": [
                        {"type": str(rel.get("type", "RELATED")), "target": str(rel.get("target", "")).strip()}
                        for rel in item.get("relations", [])
                        if str(rel.get("target", "")).strip()
                    ],
                }
            )
        return entities or self.fallback.extract(parsed, chunks)
