from rag_agent_platform.evaluation.rules import RuleBasedEvaluator
from rag_agent_platform.memory.layered_memory import LayeredMemoryStore
from rag_agent_platform.models import EvalCase, QueryRequest, RetrievalHit, RetrievalStrategy
from rag_agent_platform.retrieval.precise import PreciseBlockRetriever
from rag_agent_platform.retrieval.rrf import RRFusion
from rag_agent_platform.routing.intent_router import IntentRouter


def test_control_command_route():
    route = IntentRouter().route(QueryRequest(tenant_id="t", user_id="u", message="/clear"))
    assert route.command == "clear_conversation"


def test_rrf_fusion_merges_ranked_lists():
    hit_a = RetrievalHit("c1", "d1", "text", 0.9, "dense", "t")
    hit_b = RetrievalHit("c1", "d1", "text", 0.7, "sparse", "t")
    fused = RRFusion().fuse([[hit_a], [hit_b]], top_k=1)
    assert fused[0].chunk_id == "c1"
    assert fused[0].score > 0


def test_memory_recall_and_clear():
    store = LayeredMemoryStore()
    store.remember("t", "u", "用户关注 RAPTOR 检索", "long_term", 0.9)
    assert store.recall("t", "u")
    store.clear_session("t", "u")
    assert store.recall("t", "u")


def test_rule_evaluator_empty_recall():
    case = EvalCase(case_id="1", query="q", answer="a", hits=[])
    issues = RuleBasedEvaluator().evaluate(case)
    assert "empty_recall" in issues



def test_agentic_prefix_routes_query_to_iterative():
    route = IntentRouter().route(QueryRequest(tenant_id="t", user_id="u", message="/agentic 比较 A 和 B"))
    assert route.command is None
    assert route.strategy == RetrievalStrategy.ITERATIVE
    assert route.normalized_query == "比较 A 和 B"


def test_precise_block_filters_summary_nodes():
    class DummyHybrid:
        def retrieve(self, tenant_id, query, material_ids=None):
            return [
                RetrievalHit("s1", "d1", "summary", 0.9, "hybrid", tenant_id, metadata={"node_type": "raptor_summary"}),
                RetrievalHit("l1", "d1", "leaf", 0.8, "hybrid", tenant_id),
            ]

    hits = PreciseBlockRetriever(DummyHybrid()).retrieve("t", "q")
    assert [hit.chunk_id for hit in hits] == ["l1"]



def test_raptor_uses_summary_level_for_coarse_recall():
    from rag_agent_platform.models import Chunk
    from rag_agent_platform.raptor.tree_index import InMemoryRaptorStore
    from rag_agent_platform.raptor.retriever import RaptorRetriever
    from rag_agent_platform.storage.in_memory import HashEmbeddingClient

    leaf = Chunk("leaf", "doc", "leaf text", "t", level=0, vector=[1.0, 0.0])
    parent = Chunk("parent", "doc", "summary", "t", level=1, child_ids=["leaf"], vector=[1.0, 0.0])
    store = InMemoryRaptorStore()
    store.upsert_many([leaf, parent])

    class Backend:
        def __init__(self):
            self.levels = []

        def search(self, tenant_id, query_vector, top_k, level=None):
            self.levels.append(level)
            return [RetrievalHit("parent", "doc", "summary", 1.0, "dense", tenant_id, metadata={"level": 1})]

    backend = Backend()
    hits = RaptorRetriever(HashEmbeddingClient(), backend, store).retrieve("t", "summary")
    assert backend.levels == [1]
    assert [hit.chunk_id for hit in hits] == ["leaf"]


def test_in_memory_sparse_uses_bm25_ranking():
    from rag_agent_platform.models import Chunk
    from rag_agent_platform.storage.in_memory import InMemorySparseBackend

    backend = InMemorySparseBackend()
    backend.upsert_documents([
        Chunk("a", "doc-a", "milvus milvus vector search", "t"),
        Chunk("b", "doc-b", "unrelated content", "t"),
    ])
    hits = backend.search("t", "milvus vector", 2)
    assert hits[0].chunk_id == "a"
    assert hits[0].score > hits[1].score


def test_local_graph_multi_hop_returns_paths():
    from rag_agent_platform.retrieval.neo4j_adapter import LocalGraphRetriever

    graph = LocalGraphRetriever()
    graph.upsert_entities("t", [
        {"name": "A", "relations": [{"type": "DEPENDS_ON", "target": "B"}]},
        {"name": "B", "relations": [{"type": "LINKS", "target": "C"}]},
    ])
    paths = graph.multi_hop_search("t", ["A"], hops=2)
    assert any(path.nodes == ["A", "B", "C"] for path in paths)


def test_qwen_intent_fallback_is_query_dependent():
    from rag_agent_platform.models import IntentType
    from rag_agent_platform.routing.intent_router import QwenIntentClassifier

    classifier = QwenIntentClassifier(endpoint="")
    assert classifier.classify("Neo4j 多跳关系查询")[0] == IntentType.ENTITY_RELATION
    assert classifier.classify("比较两份资料差异")[0] == IntentType.COMPLEX_REASONING
