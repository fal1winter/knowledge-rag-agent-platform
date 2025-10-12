"""查询改写、指代消解与术语归一化。"""

from dataclasses import dataclass
import json
from typing import List, Protocol

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient


class RewriteModel(Protocol):
    def rewrite(self, query: str, context: List[str]) -> str:
        """将口语化用户输入改写为独立检索查询。"""


@dataclass
class QueryOptimizationResult:
    original: str
    rewritten: str
    filled_context: List[str]
    normalized_terms: List[str]


class QueryOptimizer:
    """检索前的标准化请求预处理。"""

    TERM_NORMALIZATION = {
        "rag": "检索增强生成",
        "知识库问答": "知识库 AI 对话",
        "多跳": "知识图谱多跳检索",
        "材料": "资料",
    }

    def __init__(self, rewrite_model: RewriteModel | None = None):
        self.rewrite_model = rewrite_model

    def optimize(self, query: str, recent_context: List[str] | None = None) -> QueryOptimizationResult:
        context = recent_context or []
        normalized = self._normalize_terms(query)
        filled = self._fill_coreference(normalized, context)
        rewritten = self.rewrite_model.rewrite(filled, context) if self.rewrite_model else filled
        terms = [target for key, target in self.TERM_NORMALIZATION.items() if key in query.lower()]
        return QueryOptimizationResult(
            original=query,
            rewritten=rewritten,
            filled_context=context[-3:],
            normalized_terms=terms,
        )

    def _normalize_terms(self, query: str) -> str:
        rewritten = query
        lower = query.lower()
        for source, target in self.TERM_NORMALIZATION.items():
            if source in lower and target not in rewritten:
                rewritten = rewritten.replace(source, target)
        return rewritten.strip()

    def _fill_coreference(self, query: str, context: List[str]) -> str:
        if not context:
            return query
        pronouns = ("它", "这个", "上述", "前面", "该资料", "这个问题")
        if any(word in query for word in pronouns):
            return f"上下文：{context[-1]}\n当前问题：{query}"
        return query


class QwenRewriteAdapter:
    """基于 Qwen 模型的查询改写适配器。"""

    def __init__(self, endpoint: str, model: str = "Qwen2.5-7B-Instruct-QLoRA", api_key: str | None = None):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key, timeout=30.0) if endpoint else None

    def rewrite(self, query: str, context: List[str]) -> str:
        if self.client is not None:
            messages = [
                {"role": "system", "content": "Rewrite colloquial RAG queries into standalone retrieval queries. Return JSON: {rewritten_query: string}."},
                {"role": "user", "content": json.dumps({"context": context[-5:], "query": query}, ensure_ascii=False)},
            ]
            try:
                raw = self.client.chat(self.model, messages, temperature=0.0, extra={"response_format": {"type": "json_object"}})
                data = json.loads(raw)
                rewritten = str(data.get("rewritten_query", "")).strip()
                return rewritten or query
            except Exception:
                return self._heuristic_rewrite(query, context)
        return self._heuristic_rewrite(query, context)

    def _heuristic_rewrite(self, query: str, context: List[str]) -> str:
        if not context:
            return query
        short_pronouns = ("它", "这个", "该", "上述", "前面")
        if any(token in query for token in short_pronouns):
            return f"{context[-1]}；{query}"
        return query

