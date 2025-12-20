"""面向复杂推理请求的多策略迭代式检索。

核心机制：
1. 首轮使用主检索器（通常 hybrid）获取初始证据
2. 质量评估器判断当前证据是否充分
3. 若不充分，由查询规划器生成子查询继续迭代
4. 若连续迭代质量无提升，自动升级到备选检索策略（图谱/RAPTOR/精准块）
5. 支持最多 N 轮迭代，每轮保留去重后的增量命中
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Callable, Dict, List, Optional, Protocol

from rag_agent_platform.models import RetrievalHit


class Retriever(Protocol):
    def retrieve(self, tenant_id: str, query: str, material_ids: List[str] | None = None) -> List[RetrievalHit]:
        """针对单条查询检索证据。"""


class QueryPlanner(Protocol):
    def decompose(self, query: str, evidence: List[RetrievalHit], failed_queries: List[str]) -> List[str]:
        """基于已有证据和失败查询，生成追加子查询用于补充检索。"""


class QualityAssessor(Protocol):
    def assess(self, query: str, hits: List[RetrievalHit]) -> "QualityVerdict":
        """评估当前检索结果对原始问题的覆盖度。"""


class FallbackStrategy(Enum):
    """迭代检索可用的回退策略。"""
    GRAPH_MULTI_HOP = "graph_multi_hop"
    RAPTOR_DRILL_DOWN = "raptor_drill_down"
    PRECISE_BLOCK = "precise_block"
    EXPAND_KEYWORDS = "expand_keywords"


@dataclass
class QualityVerdict:
    """检索质量判定结果。"""
    sufficient: bool
    coverage: float  # 0.0 ~ 1.0 证据覆盖度
    missing_aspects: List[str] = field(default_factory=list)
    suggested_fallback: Optional[FallbackStrategy] = None


@dataclass
class IterationTrace:
    round_index: int
    query: str
    retriever_used: str
    hit_count: int
    quality_coverage: float


@dataclass
class IterativeRetrievalResult:
    hits: List[RetrievalHit]
    trace: List[IterationTrace] = field(default_factory=list)
    final_coverage: float = 0.0
    strategies_used: List[str] = field(default_factory=list)
    total_rounds: int = 0


class AgenticRetriever:
    """多策略自适应迭代检索器。

    检索流程：
    1. 主检索器执行首次查询
    2. 质量评估器评判证据充分性
    3. 不充分时：
       a. 先通过子查询拆分继续用主检索器迭代
       b. 若连续 2 轮无明显增量，触发策略回退
       c. 按照评估器建议的 fallback 策略调用备选检索器
    4. 达到 max_rounds 或证据充分后返回
    """

    def __init__(
        self,
        retriever: Retriever,
        planner: QueryPlanner,
        assessor: QualityAssessor,
        fallback_retrievers: Dict[FallbackStrategy, Retriever] | None = None,
        max_rounds: int = 5,
        min_coverage_threshold: float = 0.6,
        stagnation_patience: int = 2,
    ):
        self.retriever = retriever
        self.planner = planner
        self.assessor = assessor
        self.fallback_retrievers = fallback_retrievers or {}
        self.max_rounds = max_rounds
        self.min_coverage_threshold = min_coverage_threshold
        self.stagnation_patience = stagnation_patience

    def retrieve(
        self,
        tenant_id: str,
        query: str,
        material_ids: List[str] | None = None,
    ) -> IterativeRetrievalResult:
        all_hits: List[RetrievalHit] = []
        seen_chunks: set = set()
        trace: List[IterationTrace] = []
        strategies_used: List[str] = ["hybrid"]
        failed_queries: List[str] = []

        # 首轮：主检索器
        initial_hits = self._deduplicated_retrieve(
            self.retriever, tenant_id, query, material_ids, seen_chunks
        )
        all_hits.extend(initial_hits)
        verdict = self.assessor.assess(query, all_hits)
        trace.append(IterationTrace(
            round_index=1, query=query, retriever_used="hybrid",
            hit_count=len(initial_hits), quality_coverage=verdict.coverage,
        ))

        if verdict.sufficient:
            return self._build_result(all_hits, trace, verdict.coverage, strategies_used)

        # 迭代阶段
        stagnation_count = 0
        prev_coverage = verdict.coverage
        pending_queries: List[str] = []

        for round_index in range(2, self.max_rounds + 1):
            # 生成子查询
            if not pending_queries:
                pending_queries = self.planner.decompose(query, all_hits, failed_queries)
                if not pending_queries:
                    break

            current_query = pending_queries.pop(0)

            # 判断是否应该切换策略
            if stagnation_count >= self.stagnation_patience and verdict.suggested_fallback:
                new_hits = self._try_fallback(
                    verdict.suggested_fallback, tenant_id, query,
                    material_ids, seen_chunks, strategies_used
                )
                retriever_name = verdict.suggested_fallback.value
                stagnation_count = 0  # 重置停滞计数
            else:
                new_hits = self._deduplicated_retrieve(
                    self.retriever, tenant_id, current_query, material_ids, seen_chunks
                )
                retriever_name = "hybrid"

            if not new_hits:
                failed_queries.append(current_query)
                stagnation_count += 1
            else:
                all_hits.extend(new_hits)

            # 重新评估质量
            verdict = self.assessor.assess(query, all_hits)
            trace.append(IterationTrace(
                round_index=round_index, query=current_query,
                retriever_used=retriever_name,
                hit_count=len(new_hits), quality_coverage=verdict.coverage,
            ))

            if verdict.sufficient:
                break

            # 检测停滞
            if verdict.coverage <= prev_coverage + 0.05:
                stagnation_count += 1
            else:
                stagnation_count = 0
            prev_coverage = verdict.coverage

        return self._build_result(all_hits, trace, verdict.coverage, strategies_used)

    def _try_fallback(
        self,
        strategy: FallbackStrategy,
        tenant_id: str,
        query: str,
        material_ids: List[str] | None,
        seen_chunks: set,
        strategies_used: List[str],
    ) -> List[RetrievalHit]:
        """尝试使用备选检索策略获取补充证据。"""
        fallback = self.fallback_retrievers.get(strategy)
        if fallback is None:
            return []
        strategies_used.append(strategy.value)
        return self._deduplicated_retrieve(fallback, tenant_id, query, material_ids, seen_chunks)

    def _deduplicated_retrieve(
        self,
        retriever: Retriever,
        tenant_id: str,
        query: str,
        material_ids: List[str] | None,
        seen_chunks: set,
    ) -> List[RetrievalHit]:
        """执行检索并去重。"""
        try:
            hits = retriever.retrieve(tenant_id, query, material_ids)
        except Exception:
            return []
        new_hits = [h for h in hits if h.chunk_id not in seen_chunks]
        for h in new_hits:
            seen_chunks.add(h.chunk_id)
        return new_hits

    def _build_result(
        self,
        hits: List[RetrievalHit],
        trace: List[IterationTrace],
        coverage: float,
        strategies_used: List[str],
    ) -> IterativeRetrievalResult:
        # 按分数排序，取 top 结果
        sorted_hits = sorted(hits, key=lambda h: h.score, reverse=True)
        return IterativeRetrievalResult(
            hits=sorted_hits,
            trace=trace,
            final_coverage=coverage,
            strategies_used=strategies_used,
            total_rounds=len(trace),
        )


class LLMQueryPlanner:
    """基于 LLM 的子查询规划器。

    调用 Qwen2.5-7B-Instruct 分析已有证据缺口，
    生成针对性子查询填补空白。
    """

    def __init__(self, llm_call: Callable[[str], str]):
        self.llm_call = llm_call

    def decompose(self, query: str, evidence: List[RetrievalHit], failed_queries: List[str]) -> List[str]:
        evidence_snippets = "\n".join(
            f"- [{h.chunk_id}] {h.text[:120]}" for h in evidence[:6]
        )
        failed_str = "、".join(failed_queries[-3:]) if failed_queries else "无"

        prompt = (
            f"你是一个检索规划助手。用户的原始问题是：{query}\n\n"
            f"当前已检索到的证据：\n{evidence_snippets}\n\n"
            f"之前失败的查询：{failed_str}\n\n"
            f"请分析当前证据的不足之处，生成 1~3 条补充检索查询。\n"
            f"要求：\n"
            f"1. 每条查询针对一个未覆盖的信息维度\n"
            f"2. 避免与已失败的查询重复\n"
            f"3. 查询应简洁、具体、适合向量检索\n"
            f"输出格式（每行一条查询）："
        )
        try:
            response = self.llm_call(prompt)
            queries = [q.strip().lstrip("- ").lstrip("0123456789.、") for q in response.strip().split("\n") if q.strip()]
            return queries[:3]
        except Exception:
            return HeuristicQueryPlanner().decompose(query, evidence, failed_queries)


class HeuristicQueryPlanner:
    """启发式子查询规划，无需 LLM。

    根据已有证据的覆盖情况生成不同角度的子查询。
    """

    def decompose(self, query: str, evidence: List[RetrievalHit], failed_queries: List[str]) -> List[str]:
        candidates: List[str] = []
        failed_set = set(failed_queries)

        if not evidence:
            # 无证据时尝试宽泛化和具体化两个方向
            candidates = [
                f"{query} 概念定义 背景知识",
                f"{query} 具体案例 应用场景",
            ]
        elif len(evidence) < 3:
            # 证据过少，换角度补充
            candidates = [
                f"{query} 详细分析",
                f"{query} 对比 区别",
                f"{query} 实现原理",
            ]
        else:
            # 有一定证据但不充分，尝试深入和补充反面
            candidates = [
                f"{query} 深层原因 根本机制",
                f"{query} 反例 限制条件 局限性",
                f"{query} 最新进展 改进方案",
            ]

        # 过滤已失败的查询
        return [q for q in candidates if q not in failed_set][:2]


class ScoreBasedQualityAssessor:
    """基于检索分数的质量评估器。

    综合判断依据：
    1. 高分命中数量（score > 阈值）
    2. 总命中数是否足够
    3. 命中的文档多样性（不同 document_id 数量）
    """

    def __init__(
        self,
        high_score_threshold: float = 0.7,
        min_high_score_hits: int = 3,
        min_total_hits: int = 5,
        min_doc_diversity: int = 2,
    ):
        self.high_score_threshold = high_score_threshold
        self.min_high_score_hits = min_high_score_hits
        self.min_total_hits = min_total_hits
        self.min_doc_diversity = min_doc_diversity

    def assess(self, query: str, hits: List[RetrievalHit]) -> QualityVerdict:
        if not hits:
            return QualityVerdict(
                sufficient=False,
                coverage=0.0,
                missing_aspects=["无任何检索结果"],
                suggested_fallback=FallbackStrategy.EXPAND_KEYWORDS,
            )

        high_score_hits = [h for h in hits if h.score >= self.high_score_threshold]
        unique_docs = set(h.document_id for h in hits if h.document_id)
        avg_score = sum(h.score for h in hits) / len(hits)

        # 计算覆盖度评分
        score_factor = min(1.0, len(high_score_hits) / self.min_high_score_hits)
        count_factor = min(1.0, len(hits) / self.min_total_hits)
        diversity_factor = min(1.0, len(unique_docs) / self.min_doc_diversity) if unique_docs else 0.5
        coverage = 0.5 * score_factor + 0.3 * count_factor + 0.2 * diversity_factor

        sufficient = (
            len(high_score_hits) >= self.min_high_score_hits
            and len(hits) >= self.min_total_hits
            and coverage >= 0.7
        )

        # 推断缺失维度和建议回退
        missing_aspects: List[str] = []
        suggested_fallback: Optional[FallbackStrategy] = None

        if len(high_score_hits) < self.min_high_score_hits:
            missing_aspects.append("高相关性命中不足")
            suggested_fallback = FallbackStrategy.RAPTOR_DRILL_DOWN

        if len(unique_docs) < self.min_doc_diversity:
            missing_aspects.append("来源多样性不足")
            suggested_fallback = FallbackStrategy.EXPAND_KEYWORDS

        if avg_score < 0.4:
            missing_aspects.append("整体相关性偏低")
            suggested_fallback = FallbackStrategy.GRAPH_MULTI_HOP

        return QualityVerdict(
            sufficient=sufficient,
            coverage=coverage,
            missing_aspects=missing_aspects,
            suggested_fallback=suggested_fallback,
        )


class LLMQualityAssessor:
    """基于 LLM 的检索质量评估器。

    调用 Qwen 判断当前证据是否足以回答用户问题，
    输出覆盖度打分和缺失维度分析。
    """

    def __init__(self, llm_call: Callable[[str], str]):
        self.llm_call = llm_call
        self._score_assessor = ScoreBasedQualityAssessor()

    def assess(self, query: str, hits: List[RetrievalHit]) -> QualityVerdict:
        # 先做快速分数判断，明显充分或明显不足时跳过 LLM 调用
        quick = self._score_assessor.assess(query, hits)
        if quick.coverage >= 0.85 or quick.coverage <= 0.15:
            return quick

        evidence_text = "\n".join(
            f"[{i+1}] (score={h.score:.2f}) {h.text[:150]}"
            for i, h in enumerate(hits[:8])
        )
        prompt = (
            f"用户问题：{query}\n\n"
            f"当前检索到的证据：\n{evidence_text}\n\n"
            f"请评估这些证据是否足以回答用户问题。输出格式：\n"
            f"覆盖度：0.0~1.0 的小数\n"
            f"是否充分：是/否\n"
            f"缺失维度：逗号分隔的缺失信息方向\n"
            f"建议策略：graph_multi_hop / raptor_drill_down / precise_block / expand_keywords / 无"
        )
        try:
            response = self.llm_call(prompt)
            return self._parse_llm_response(response, quick)
        except Exception:
            return quick

    def _parse_llm_response(self, response: str, fallback: QualityVerdict) -> QualityVerdict:
        """解析 LLM 输出的结构化评估结果。"""
        try:
            lines = response.strip().split("\n")
            coverage = 0.5
            sufficient = False
            missing_aspects: List[str] = []
            suggested_fallback: Optional[FallbackStrategy] = None

            for line in lines:
                if "覆盖度" in line:
                    nums = [c for c in line.split("：")[-1].strip() if c.isdigit() or c == "."]
                    coverage = float("".join(nums)) if nums else 0.5
                    coverage = min(1.0, max(0.0, coverage))
                elif "是否充分" in line:
                    sufficient = "是" in line.split("：")[-1] and "否" not in line.split("：")[-1]
                elif "缺失维度" in line:
                    aspects = line.split("：")[-1].strip()
                    if aspects and aspects != "无":
                        missing_aspects = [a.strip() for a in aspects.split("，") if a.strip()]
                elif "建议策略" in line:
                    strategy_str = line.split("：")[-1].strip()
                    strategy_map = {
                        "graph_multi_hop": FallbackStrategy.GRAPH_MULTI_HOP,
                        "raptor_drill_down": FallbackStrategy.RAPTOR_DRILL_DOWN,
                        "precise_block": FallbackStrategy.PRECISE_BLOCK,
                        "expand_keywords": FallbackStrategy.EXPAND_KEYWORDS,
                    }
                    suggested_fallback = strategy_map.get(strategy_str)

            return QualityVerdict(
                sufficient=sufficient,
                coverage=coverage,
                missing_aspects=missing_aspects,
                suggested_fallback=suggested_fallback,
            )
        except Exception:
            return fallback
