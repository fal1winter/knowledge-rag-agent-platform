"""端到端 RAG Agent 编排调度。"""

from dataclasses import dataclass
from typing import List, Protocol

from rag_agent_platform.generation.answer_service import AnswerService
from rag_agent_platform.models import Answer, QueryRequest, RetrievalHit, RetrievalStrategy
from rag_agent_platform.retrieval.agentic import AgenticRetriever
from rag_agent_platform.retrieval.hybrid import HybridRetriever
from rag_agent_platform.retrieval.neo4j_adapter import Neo4jGraphRetriever
from rag_agent_platform.retrieval.precise import PreciseBlockRetriever
from rag_agent_platform.routing.intent_router import IntentRouter


class RaptorRetrieverLike(Protocol):
    def retrieve(self, tenant_id: str, query: str) -> List[RetrievalHit]:
        """RAPTOR 粗召回到下钻检索。"""


@dataclass
class AgentDependencies:
    router: IntentRouter
    hybrid_retriever: HybridRetriever
    precise_retriever: PreciseBlockRetriever
    raptor_retriever: RaptorRetrieverLike
    graph_retriever: Neo4jGraphRetriever
    agentic_retriever: AgenticRetriever
    answer_service: AnswerService


class KnowledgeRagAgent:
    """路由、检索、生成一条知识库回答。"""

    def __init__(self, deps: AgentDependencies):
        self.deps = deps

    def handle(self, request: QueryRequest, recent_context: List[str] | None = None) -> Answer:
        route = self.deps.router.route(request, recent_context)
        if route.command:
            return Answer(
                text=self._handle_command(route.command),
                route=route,
                citations=[],
                retrieval_hits=[],
                debug={"command": route.command},
            )

        hits = self._retrieve(request, route.strategy, route.normalized_query, route.entities)
        text, citations = self.deps.answer_service.answer(route.normalized_query, hits)
        return Answer(
            text=text,
            route=route,
            citations=citations,
            retrieval_hits=hits,
            debug={"strategy": route.strategy.value, "hit_count": len(hits)},
        )

    def _retrieve(
        self,
        request: QueryRequest,
        strategy: RetrievalStrategy,
        query: str,
        entities: List[str],
    ) -> List[RetrievalHit]:
        if strategy == RetrievalStrategy.GRAPH_MULTI_HOP:
            paths = self.deps.graph_retriever.multi_hop_search(request.tenant_id, entities)
            return self.deps.graph_retriever.to_hits(request.tenant_id, paths)
        if strategy == RetrievalStrategy.ITERATIVE:
            return self.deps.agentic_retriever.retrieve(
                request.tenant_id,
                query,
                material_ids=request.material_ids,
            ).hits
        if strategy == RetrievalStrategy.RAPTOR:
            return self.deps.raptor_retriever.retrieve(request.tenant_id, query)
        if strategy == RetrievalStrategy.PRECISE_BLOCK:
            return self.deps.precise_retriever.retrieve(
                request.tenant_id,
                query,
                material_ids=request.material_ids,
            )
        return self.deps.hybrid_retriever.retrieve(
            request.tenant_id,
            query,
            material_ids=request.material_ids,
        )

    def _handle_command(self, command: str) -> str:
        responses = {
            "clear_conversation": "已清除当前会话上下文。",
            "toggle_persistent_context": "已切换持久上下文模式。",
            "force_iterative_retrieval": "已开启强制迭代检索。",
        }
        return responses.get(command, "指令已接收。")

