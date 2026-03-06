"""三级意图路由。

第一级：专用控制指令解析。
第二级：关键词/正则确定性匹配。
第三级：轻量模型意图分类。
"""

from dataclasses import dataclass
import json
import re
from typing import List, Protocol

from rag_agent_platform.models import IntentType, QueryRequest, RetrievalStrategy, RouteDecision
from rag_agent_platform.integrations.http_json import OpenAICompatibleClient
from rag_agent_platform.routing.commands import ControlCommandParser
from rag_agent_platform.routing.query_optimizer import QueryOptimizer


class IntentModel(Protocol):
    def classify(self, query: str) -> tuple[IntentType, float, str]:
        """返回意图类型、置信度和推理说明。"""


@dataclass
class KeywordRule:
    pattern: str
    intent: IntentType
    strategy: RetrievalStrategy


class IntentRouter:
    def __init__(
        self,
        optimizer: QueryOptimizer | None = None,
        intent_model: IntentModel | None = None,
    ):
        self.commands = ControlCommandParser()
        self.optimizer = optimizer or QueryOptimizer()
        self.intent_model = intent_model
        self.keyword_rules: List[KeywordRule] = [
            KeywordRule(r"(关系|关联|上下游|属于|谁和谁|路径|多跳)", IntentType.ENTITY_RELATION, RetrievalStrategy.GRAPH_MULTI_HOP),
            KeywordRule(r"(推理|综合|比较|归因|多篇|跨章节|agentic|迭代)", IntentType.COMPLEX_REASONING, RetrievalStrategy.ITERATIVE),
            KeywordRule(r"(资料|课件|文档|PDF|PPT|Excel|知识库|章节)", IntentType.MATERIAL_SEARCH, RetrievalStrategy.RAPTOR),
            KeywordRule(r"(是什么|怎么做|解释|总结|列出|在哪)", IntentType.DIRECT_QA, RetrievalStrategy.PRECISE_BLOCK),
        ]

    def route(self, request: QueryRequest, recent_context: List[str] | None = None) -> RouteDecision:
        """三级路由：控制指令 → 关键词匹配 → 轻量模型分类，逐级降级。"""
        stripped = request.message.strip()
        has_agentic_query = stripped.lower().startswith("/agentic ")
        if request.force_agentic or has_agentic_query:
            query_text = stripped.split(maxsplit=1)[1] if has_agentic_query else request.message
            optimized = self.optimizer.optimize(query_text, recent_context)
            return RouteDecision(
                intent=IntentType.COMPLEX_REASONING,
                strategy=RetrievalStrategy.ITERATIVE,
                normalized_query=optimized.rewritten,
                confidence=1.0,
                reasoning="forced iterative retrieval",
            )

        command = self.commands.parse(request.message)
        optimized = self.optimizer.optimize(request.message, recent_context)
        if command:
            return RouteDecision(
                intent=IntentType.CONTROL,
                strategy=RetrievalStrategy.PRECISE_BLOCK,
                normalized_query=optimized.rewritten,
                confidence=1.0,
                command=command.name,
                reasoning="matched dedicated control command",
            )

        for rule in self.keyword_rules:
            if re.search(rule.pattern, request.message, flags=re.IGNORECASE):
                return RouteDecision(
                    intent=rule.intent,
                    strategy=rule.strategy,
                    normalized_query=optimized.rewritten,
                    confidence=0.86,
                    entities=self._extract_entities(optimized.rewritten),
                    reasoning=f"keyword rule matched: {rule.pattern}",
                )

        if self.intent_model:
            intent, confidence, reason = self.intent_model.classify(optimized.rewritten)
            return RouteDecision(
                intent=intent,
                strategy=self._strategy_for_intent(intent),
                normalized_query=optimized.rewritten,
                confidence=confidence,
                entities=self._extract_entities(optimized.rewritten),
                reasoning=reason,
            )

        return RouteDecision(
            intent=IntentType.UNKNOWN,
            strategy=RetrievalStrategy.HYBRID,
            normalized_query=optimized.rewritten,
            confidence=0.5,
            entities=self._extract_entities(optimized.rewritten),
            reasoning="fallback hybrid retrieval",
        )

    def _strategy_for_intent(self, intent: IntentType) -> RetrievalStrategy:
        return {
            IntentType.ENTITY_RELATION: RetrievalStrategy.GRAPH_MULTI_HOP,
            IntentType.COMPLEX_REASONING: RetrievalStrategy.ITERATIVE,
            IntentType.MATERIAL_SEARCH: RetrievalStrategy.RAPTOR,
            IntentType.DIRECT_QA: RetrievalStrategy.PRECISE_BLOCK,
        }.get(intent, RetrievalStrategy.HYBRID)

    def _extract_entities(self, query: str) -> List[str]:
        tokens = re.findall(r"[\u4e00-\u9fa5A-Za-z0-9_.-]{2,}", query)
        stopwords = {"什么", "如何", "资料", "文档", "上下文", "当前问题"}
        return [token for token in tokens[:12] if token not in stopwords]


class QwenIntentClassifier:
    """基于 Qwen 的意图分类器，模型不可用时走规则降级。"""

    def __init__(self, endpoint: str, model: str = "Qwen2.5-1.5B-Instruct-QLoRA", api_key: str | None = None):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key, timeout=20.0) if endpoint else None

    def classify(self, query: str) -> tuple[IntentType, float, str]:
        if self.client is not None:
            labels = [intent.value for intent in IntentType if intent is not IntentType.CONTROL]
            messages = [
                {"role": "system", "content": "Classify the user query for RAG routing. Return compact JSON with intent, confidence, reasoning."},
                {"role": "user", "content": json.dumps({"query": query, "labels": labels}, ensure_ascii=False)},
            ]
            try:
                raw = self.client.chat(self.model, messages, temperature=0.0, extra={"response_format": {"type": "json_object"}})
                data = json.loads(raw)
                intent = IntentType(data.get("intent", IntentType.UNKNOWN.value))
                confidence = float(data.get("confidence", 0.0))
                return intent, max(0.0, min(1.0, confidence)), str(data.get("reasoning", "qwen intent classifier"))
            except Exception as exc:
                fallback = self._fallback(query)
                return fallback[0], fallback[1], f"qwen_call_failed:{exc.__class__.__name__}; {fallback[2]}"
        return self._fallback(query)

    def _fallback(self, query: str) -> tuple[IntentType, float, str]:
        lowered = query.lower()
        if any(word in query for word in ("关系", "关联", "路径", "多跳")):
            return IntentType.ENTITY_RELATION, 0.64, "local lexical fallback"
        if any(word in query for word in ("推理", "综合", "比较", "归因", "跨章节")) or "agentic" in lowered:
            return IntentType.COMPLEX_REASONING, 0.64, "local lexical fallback"
        if any(word in query for word in ("资料", "文档", "pdf", "ppt", "excel", "知识库")):
            return IntentType.MATERIAL_SEARCH, 0.62, "local lexical fallback"
        return IntentType.DIRECT_QA, 0.58, "local lexical fallback"

