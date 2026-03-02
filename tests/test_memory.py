"""三层记忆模块单元测试。"""

import pytest

from rag_agent_platform.memory.layered_memory import LayeredMemoryStore


class TestLayeredMemoryStore:
    @pytest.fixture
    def store(self):
        return LayeredMemoryStore()

    def test_remember_and_recall(self, store):
        store.remember("t1", "u1", "用户关注 RAPTOR 检索", "long_term", 0.9)
        memories = store.recall("t1", "u1")
        assert len(memories) > 0
        assert any("RAPTOR" in m.content for m in memories)

    def test_session_memory_separate_from_long_term(self, store):
        store.remember("t1", "u1", "短期会话内容", "short_term", 0.8)
        store.remember("t1", "u1", "长期用户画像", "long_term", 0.9)
        memories = store.recall("t1", "u1")
        assert len(memories) >= 2

    def test_clear_session_removes_only_session(self, store):
        store.remember("t1", "u1", "短期内容", "short_term", 0.8)
        store.remember("t1", "u1", "长期内容", "long_term", 0.9)
        store.clear_session("t1", "u1")
        memories = store.recall("t1", "u1")
        # 长期记忆应该保留
        assert any("长期内容" in m.content for m in memories)

    def test_different_users_isolated(self, store):
        store.remember("t1", "u1", "用户1的记忆", "long_term", 0.9)
        store.remember("t1", "u2", "用户2的记忆", "long_term", 0.9)
        memories_u1 = store.recall("t1", "u1")
        memories_u2 = store.recall("t1", "u2")
        assert all("用户1" in m.content for m in memories_u1)
        assert all("用户2" in m.content for m in memories_u2)

    def test_different_tenants_isolated(self, store):
        store.remember("t1", "u1", "租户1的数据", "long_term", 0.9)
        store.remember("t2", "u1", "租户2的数据", "long_term", 0.9)
        memories_t1 = store.recall("t1", "u1")
        memories_t2 = store.recall("t2", "u1")
        assert all("租户1" in m.content for m in memories_t1)
        assert all("租户2" in m.content for m in memories_t2)

    def test_invalid_layer_raises(self, store):
        with pytest.raises(ValueError, match="Unknown memory layer"):
            store.remember("t1", "u1", "内容", "invalid_layer", 0.5)

    def test_session_summary_layer(self, store):
        store.remember("t1", "u1", "本次对话摘要", "session_summary", 0.7)
        memories = store.recall("t1", "u1")
        assert any("对话摘要" in m.content for m in memories)
