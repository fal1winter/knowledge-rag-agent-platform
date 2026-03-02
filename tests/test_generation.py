"""生成模块单测。"""

import pytest

from rag_agent_platform.generation.llm_client import (
    DeepSeekChatAdapter,
    PromptBuilder,
    QwenChatAdapter,
)
from rag_agent_platform.generation.answer_service import AnswerService
from rag_agent_platform.models import RetrievalHit


class TestDeepSeekChatAdapter:
    def test_fallback_on_unreachable_endpoint(self):
        adapter = DeepSeekChatAdapter(endpoint="http://127.0.0.1:1/v1", api_key="fake")
        messages = [
            {"role": "system", "content": "系统提示"},
            {"role": "user", "content": "问题：什么是 RAG\n\n证据：\n[1] RAG 是检索增强生成"},
        ]
        result = adapter.complete(messages)
        # 端点不可达时应返回降级回答
        assert "检索证据" in result or "不可达" in result

    def test_fallback_extracts_evidence(self):
        adapter = DeepSeekChatAdapter(endpoint="", api_key="")
        messages = [
            {"role": "user", "content": "问题：向量检索\n\n证据：\n[1] Milvus 是向量数据库"},
        ]
        result = adapter.complete(messages)
        assert "Milvus" in result or "不可达" in result


class TestQwenChatAdapter:
    def test_fallback_extractive_answer(self):
        adapter = QwenChatAdapter(endpoint="", fallback_enabled=True)
        messages = [
            {"role": "user", "content": "证据：RAPTOR 使用 k-means 聚类构建摘要树\n\n请回答。"},
        ]
        result = adapter.complete(messages)
        assert "RAPTOR" in result or "检索证据" in result

    def test_no_fallback_raises(self):
        adapter = QwenChatAdapter(endpoint="http://127.0.0.1:1/v1", fallback_enabled=False)
        messages = [{"role": "user", "content": "测试"}]
        with pytest.raises(Exception):
            adapter.complete(messages)


class TestPromptBuilder:
    def test_builds_answer_messages(self):
        builder = PromptBuilder()
        evidence = [
            {"text": "RAPTOR 是树状索引", "citation": {"doc": "d1"}, "score": 0.9, "source": "hybrid"},
        ]
        messages = builder.build_answer_messages("什么是 RAPTOR", evidence)
        assert len(messages) == 2
        assert messages[0]["role"] == "system"
        assert "RAPTOR" in messages[1]["content"]
        assert "[1]" in messages[1]["content"]


class TestAnswerService:
    def test_integrates_chat_model_and_builder(self):
        class FakeModel:
            def complete(self, messages, temperature=0.2):
                return "RAPTOR 是一种树状层级检索结构 [1]"

        service = AnswerService(FakeModel())
        hits = [
            RetrievalHit("c1", "d1", "RAPTOR 树状索引", 0.9, "hybrid", "t", citation={"doc": "d1"}),
        ]
        text, citations = service.answer("什么是 RAPTOR", hits)
        assert "RAPTOR" in text
        assert len(citations) == 1
