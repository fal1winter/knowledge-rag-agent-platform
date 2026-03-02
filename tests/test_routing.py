"""意图路由模块单元测试。"""

import pytest

from rag_agent_platform.models import IntentType, QueryRequest, RetrievalStrategy
from rag_agent_platform.routing.commands import ControlCommandParser
from rag_agent_platform.routing.intent_router import IntentRouter, QwenIntentClassifier
from rag_agent_platform.routing.query_optimizer import QueryOptimizer


class TestControlCommands:
    def test_clear_command(self):
        parser = ControlCommandParser()
        result = parser.parse("/clear")
        assert result is not None
        assert result.name == "clear_conversation"

    def test_context_command(self):
        parser = ControlCommandParser()
        result = parser.parse("/context")
        assert result is not None
        assert result.name == "toggle_persistent_context"

    def test_non_command_returns_none(self):
        parser = ControlCommandParser()
        result = parser.parse("普通问题")
        assert result is None

    def test_agentic_command_extracts_argument(self):
        parser = ControlCommandParser()
        result = parser.parse("/agentic 比较两份报告")
        assert result is not None
        assert result.name == "force_iterative_retrieval"


class TestIntentRouter:
    @pytest.fixture
    def router(self):
        return IntentRouter()

    def test_control_command_routing(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="/clear")
        route = router.route(req)
        assert route.command == "clear_conversation"

    def test_graph_keyword_routing(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="A 和 B 之间的关系是什么")
        route = router.route(req)
        assert route.strategy == RetrievalStrategy.GRAPH_MULTI_HOP

    def test_iterative_keyword_routing(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="综合比较多篇文献的结论")
        route = router.route(req)
        assert route.strategy == RetrievalStrategy.ITERATIVE

    def test_raptor_keyword_routing(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="这份 PDF 资料里有什么内容")
        route = router.route(req)
        assert route.strategy == RetrievalStrategy.RAPTOR

    def test_agentic_prefix_forces_iterative(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="/agentic 比较 A 和 B")
        route = router.route(req)
        assert route.strategy == RetrievalStrategy.ITERATIVE

    def test_force_agentic_flag(self, router):
        req = QueryRequest(tenant_id="t", user_id="u", message="随便一个问题", force_agentic=True)
        route = router.route(req)
        assert route.strategy == RetrievalStrategy.ITERATIVE

    def test_default_fallback_strategy(self, router):
        """无关键词匹配时走默认策略。"""
        req = QueryRequest(tenant_id="t", user_id="u", message="你好")
        route = router.route(req)
        # 不匹配任何关键词，走分类器或默认
        assert route.strategy is not None


class TestQueryOptimizer:
    def test_optimize_without_adapter(self):
        optimizer = QueryOptimizer()
        result = optimizer.optimize("原始查询")
        assert result.rewritten == "原始查询"

    def test_optimize_strips_whitespace(self):
        optimizer = QueryOptimizer()
        result = optimizer.optimize("  带空格的查询  ")
        assert "带空格的查询" in result.rewritten


class TestQwenIntentClassifier:
    def test_fallback_entity_relation(self):
        """无端点时走启发式分类，实体关系关键词应正确识别。"""
        classifier = QwenIntentClassifier(endpoint="")
        intent, confidence, _ = classifier.classify("Neo4j 多跳关系查询")
        assert intent == IntentType.ENTITY_RELATION

    def test_fallback_complex_reasoning(self):
        classifier = QwenIntentClassifier(endpoint="")
        intent, confidence, _ = classifier.classify("综合比较两份资料差异")
        assert intent == IntentType.COMPLEX_REASONING

    def test_fallback_direct_qa(self):
        classifier = QwenIntentClassifier(endpoint="")
        intent, confidence, _ = classifier.classify("什么是向量检索")
        assert intent == IntentType.DIRECT_QA
