"""端到端 RAG Agent 编排调度。

核心职责：
1. 接收路由决策，选择对应检索策略
2. 执行检索后评估结果质量
3. 质量不足时自动降级到迭代检索进行补充
4. 将最终证据交给生成层合成回答
"""

from dataclasses import dataclass
from typing import List, Optional, Protocol

from rag_agent_platform.generation.answer_service import AnswerService
from rag_agent_platform.models import Answer, QueryRequest, RetrievalHit, RetrievalStrategy
from rag_agent_platform.retrieval.agentic import (
    AgenticRetriever,
    FallbackStrategy,
    QualityVerdict,
    ScoreBasedQualityAssessor,
)
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
    """路由、检索、生成一条知识库回答。

    支持自适应质量守卫：当首选策略的检索质量不足时，
    自动升级到迭代检索进行多轮补充。
    """

    def __init__(self, deps: AgentDependencies, enable_quality_guard: bool = True):
        self.deps = deps
        self.enable_quality_guard = enable_quality_guard
        self._assessor = ScoreBasedQualityAssessor()

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

        hits, retrieval_debug = self._retrieve_with_quality_guard(
            request, route.strategy, route.normalized_query, route.entities
        )
        text, citations = self.deps.answer_service.answer(route.normalized_query, hits)
        return Answer(
            text=text,
            route=route,
            citations=citations,
            retrieval_hits=hits,
            debug={
                "strategy": route.strategy.value,
                "hit_count": len(hits),
                **retrieval_debug,
            },
        )

    def _retrieve_with_quality_guard(
        self,
        request: QueryRequest,
        strategy: RetrievalStrategy,
        query: str,
        entities: List[str],
    ) -> tuple:
        """执行检索并进行质量守卫。

        若首选策略检索质量不足且当前策略不是迭代检索，
        自动升级到 AgenticRetriever 进行多策略补充。
        """
        hits = self._retrieve(request, strategy, query, entities)
        debug = {"initial_strategy": strategy.value, "initial_hits": len(hits)}

        # 迭代检索本身已含质量评估，不需要外层守卫
        if strategy == RetrievalStrategy.ITERATIVE or not self.enable_quality_guard:
            return hits, debug

        # 质量评估
        verdict = self._assessor.assess(query, hits)
        debug["initial_coverage"] = verdict.coverage

        if verdict.sufficient:
            return hits, debug

        # 质量不足，升级到迭代检索补充证据
        debug["quality_guard_triggered"] = True
        debug["missing_aspects"] = verdict.missing_aspects

        iterative_result = self.deps.agentic_retriever.retrieve(
            request.tenant_id,
            query,
            material_ids=request.material_ids,
        )

        # 合并去重
        seen_chunks = set(h.chunk_id for h in hits)
        supplementary = [h for h in iterative_result.hits if h.chunk_id not in seen_chunks]
        combined = hits + supplementary

        # 按分数排序取 top
        combined.sort(key=lambda h: h.score, reverse=True)
        final_hits = combined[:15]

        debug["supplementary_hits"] = len(supplementary)
        debug["final_coverage"] = iterative_result.final_coverage
        debug["strategies_used"] = iterative_result.strategies_used
        debug["total_iterative_rounds"] = iterative_result.total_rounds

        return final_hits, debug

    def _retrieve(
        self,
        request: QueryRequest,
        strategy: RetrievalStrategy,
        query: str,
        entities: List[str],
    ) -> List[RetrievalHit]:
        """按路由策略调用对应检索器。"""
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

