"""Agentic 迭代检索单测。"""

import pytest

from rag_agent_platform.models import RetrievalHit
from rag_agent_platform.retrieval.agentic import (
    AgenticRetriever,
    FallbackStrategy,
    HeuristicQueryPlanner,
    QualityVerdict,
    ScoreBasedQualityAssessor,
)


class FakeRetriever:
    """可控的虚拟检索器。"""

    def __init__(self, results_by_call=None):
        self.calls = []
        self.results_by_call = results_by_call or []
        self._call_index = 0

    def retrieve(self, tenant_id, query, material_ids=None):
        self.calls.append((tenant_id, query, material_ids))
        if self._call_index < len(self.results_by_call):
            result = self.results_by_call[self._call_index]
        else:
            result = []
        self._call_index += 1
        return result


class AlwaysSufficientAssessor:
    def assess(self, query, hits):
        return QualityVerdict(sufficient=True, coverage=0.95)


class NeverSufficientAssessor:
    def assess(self, query, hits):
        return QualityVerdict(
            sufficient=False,
            coverage=0.3,
            missing_aspects=["缺少关键信息"],
            suggested_fallback=FallbackStrategy.EXPAND_KEYWORDS,
        )


class TestScoreBasedQualityAssessor:
    @pytest.fixture
    def assessor(self):
        return ScoreBasedQualityAssessor()

    def test_empty_hits_insufficient(self, assessor):
        verdict = assessor.assess("问题", [])
        assert not verdict.sufficient
        assert verdict.coverage == 0.0
        assert verdict.suggested_fallback == FallbackStrategy.EXPAND_KEYWORDS

    def test_high_quality_hits_sufficient(self, assessor):
        hits = [
            RetrievalHit(f"c{i}", f"d{i % 3}", f"高质量内容 {i}", 0.85, "hybrid", "t")
            for i in range(6)
        ]
        verdict = assessor.assess("问题", hits)
        assert verdict.sufficient
        assert verdict.coverage >= 0.7

    def test_low_score_hits_insufficient(self, assessor):
        hits = [
            RetrievalHit(f"c{i}", "d1", f"低相关内容 {i}", 0.3, "hybrid", "t")
            for i in range(3)
        ]
        verdict = assessor.assess("问题", hits)
        assert not verdict.sufficient
        assert "整体相关性偏低" in verdict.missing_aspects

    def test_low_diversity_flagged(self, assessor):
        # 全部来自同一文档
        hits = [
            RetrievalHit(f"c{i}", "d1", f"内容 {i}", 0.8, "hybrid", "t")
            for i in range(5)
        ]
        verdict = assessor.assess("问题", hits)
        if not verdict.sufficient:
            assert "来源多样性不足" in verdict.missing_aspects


class TestHeuristicQueryPlanner:
    @pytest.fixture
    def planner(self):
        return HeuristicQueryPlanner()

    def test_no_evidence_generates_broad_queries(self, planner):
        queries = planner.decompose("什么是 RAPTOR", [], [])
        assert len(queries) > 0
        assert any("概念" in q or "定义" in q for q in queries)

    def test_few_evidence_generates_supplements(self, planner):
        hits = [RetrievalHit("c1", "d1", "部分内容", 0.7, "hybrid", "t")]
        queries = planner.decompose("RAPTOR 原理", hits, [])
        assert len(queries) > 0

    def test_filters_failed_queries(self, planner):
        hits = [RetrievalHit(f"c{i}", "d1", f"内容 {i}", 0.7, "hybrid", "t") for i in range(4)]
        failed = ["RAPTOR 原理 深层原因 根本机制"]
        queries = planner.decompose("RAPTOR 原理", hits, failed)
        assert failed[0] not in queries


class TestAgenticRetriever:
    def test_sufficient_first_round_stops_early(self):
        hits = [
            RetrievalHit(f"c{i}", f"d{i}", f"内容 {i}", 0.85, "hybrid", "t")
            for i in range(5)
        ]
        retriever = FakeRetriever(results_by_call=[hits])
        agentic = AgenticRetriever(
            retriever=retriever,
            planner=HeuristicQueryPlanner(),
            assessor=AlwaysSufficientAssessor(),
            max_rounds=5,
        )
        result = agentic.retrieve("t", "问题")
        assert result.total_rounds == 1
        assert len(result.hits) == 5
        assert result.final_coverage >= 0.9

    def test_iterates_when_insufficient(self):
        round1 = [RetrievalHit("c1", "d1", "初始内容", 0.6, "hybrid", "t")]
        round2 = [RetrievalHit("c2", "d2", "补充内容", 0.7, "hybrid", "t")]
        round3 = [RetrievalHit("c3", "d3", "更多内容", 0.65, "hybrid", "t")]
        retriever = FakeRetriever(results_by_call=[round1, round2, round3])

        call_count = [0]

        class CountingAssessor:
            def assess(self, query, hits):
                call_count[0] += 1
                if call_count[0] >= 3:
                    return QualityVerdict(sufficient=True, coverage=0.8)
                return QualityVerdict(sufficient=False, coverage=0.4, missing_aspects=["不足"])

        agentic = AgenticRetriever(
            retriever=retriever,
            planner=HeuristicQueryPlanner(),
            assessor=CountingAssessor(),
            max_rounds=5,
        )
        result = agentic.retrieve("t", "复杂问题")
        assert result.total_rounds > 1
        assert len(result.hits) > 1

    def test_max_rounds_limits_iterations(self):
        hits = [RetrievalHit("c1", "d1", "内容", 0.5, "hybrid", "t")]
        retriever = FakeRetriever(results_by_call=[hits] * 10)
        agentic = AgenticRetriever(
            retriever=retriever,
            planner=HeuristicQueryPlanner(),
            assessor=NeverSufficientAssessor(),
            max_rounds=3,
        )
        result = agentic.retrieve("t", "问题")
        assert result.total_rounds <= 3

    def test_fallback_strategy_triggered_on_stagnation(self):
        hits = [RetrievalHit("c1", "d1", "固定内容", 0.5, "hybrid", "t")]
        retriever = FakeRetriever(results_by_call=[hits, [], [], []])

        fallback_called = [False]

        class FallbackRetriever:
            def retrieve(self, tenant_id, query, material_ids=None):
                fallback_called[0] = True
                return [RetrievalHit("fb1", "d2", "回退内容", 0.7, "graph", tenant_id)]

        agentic = AgenticRetriever(
            retriever=retriever,
            planner=HeuristicQueryPlanner(),
            assessor=NeverSufficientAssessor(),
            fallback_retrievers={FallbackStrategy.EXPAND_KEYWORDS: FallbackRetriever()},
            max_rounds=5,
            stagnation_patience=2,
        )
        result = agentic.retrieve("t", "问题")
        assert fallback_called[0]
        assert "expand_keywords" in result.strategies_used

    def test_deduplicates_across_rounds(self):
        same_hit = RetrievalHit("c1", "d1", "内容", 0.8, "hybrid", "t")
        retriever = FakeRetriever(results_by_call=[[same_hit], [same_hit], [same_hit]])

        class TwoRoundAssessor:
            def __init__(self):
                self.count = 0

            def assess(self, query, hits):
                self.count += 1
                if self.count >= 2:
                    return QualityVerdict(sufficient=True, coverage=0.9)
                return QualityVerdict(sufficient=False, coverage=0.4, missing_aspects=["不足"])

        agentic = AgenticRetriever(
            retriever=retriever,
            planner=HeuristicQueryPlanner(),
            assessor=TwoRoundAssessor(),
            max_rounds=5,
        )
        result = agentic.retrieve("t", "问题")
        # 相同 chunk_id 不应重复出现
        chunk_ids = [h.chunk_id for h in result.hits]
        assert len(chunk_ids) == len(set(chunk_ids))
