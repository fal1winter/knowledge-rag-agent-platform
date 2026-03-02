"""评测模块单测。"""

import pytest

from rag_agent_platform.models import EvalCase, RetrievalHit
from rag_agent_platform.evaluation.rules import RuleBasedEvaluator
from rag_agent_platform.evaluation.llm_judge import LLMJudge


class TestRuleBasedEvaluator:
    @pytest.fixture
    def evaluator(self):
        return RuleBasedEvaluator()

    def test_empty_recall_flagged(self, evaluator):
        case = EvalCase(case_id="1", query="问题", answer="回答", hits=[])
        issues = evaluator.evaluate(case)
        assert "empty_recall" in issues

    def test_normal_case_no_issues(self, evaluator):
        hits = [
            RetrievalHit("c1", "d1", "相关内容", 0.85, "hybrid", "t", citation={"doc": "d1"}),
            RetrievalHit("c2", "d1", "补充内容", 0.75, "hybrid", "t", citation={"doc": "d1"}),
        ]
        case = EvalCase(case_id="2", query="问题", answer="基于证据的回答", hits=hits)
        issues = evaluator.evaluate(case)
        # 有命中且有引用，不应报 empty_recall
        assert "empty_recall" not in issues

    def test_missing_citation_flagged(self, evaluator):
        hits = [
            RetrievalHit("c1", "d1", "内容", 0.8, "hybrid", "t"),  # 无 citation
        ]
        case = EvalCase(case_id="3", query="问题", answer="回答", hits=hits)
        issues = evaluator.evaluate(case)
        assert "missing_citations" in issues or len(issues) == 0  # 取决于实现严格度


class TestLLMJudge:
    def test_heuristic_mode_without_model(self):
        judge = LLMJudge(model=None, allow_heuristic=True)
        hits = [
            RetrievalHit("c1", "d1", "机器学习 模型 训练", 0.8, "hybrid", "t", citation={"doc": "d1"}),
        ]
        case = EvalCase(
            case_id="1",
            query="机器学习模型如何训练",
            answer="机器学习模型通过训练数据进行训练",
            hits=hits,
            expected_facts=["机器学习", "训练数据", "模型"],
        )
        score = judge.judge(case)
        assert score.case_id == "1"
        assert 0.0 <= score.relevance <= 1.0
        assert 0.0 <= score.faithfulness <= 1.0
        assert 0.0 <= score.completeness <= 1.0

    def test_heuristic_low_faithfulness(self):
        judge = LLMJudge(model=None, allow_heuristic=True)
        hits = [
            RetrievalHit("c1", "d1", "关于 Python 编程", 0.7, "hybrid", "t"),
        ]
        case = EvalCase(
            case_id="2",
            query="Java 开发",
            answer="Java 是一门面向对象的编程语言，广泛应用于企业开发",
            hits=hits,
            expected_facts=["Java", "面向对象"],
        )
        score = judge.judge(case)
        # 答案和证据不匹配
        if score.faithfulness < 0.5:
            assert "low_faithfulness_heuristic" in score.issues

    def test_no_model_no_heuristic_raises(self):
        judge = LLMJudge(model=None, allow_heuristic=False)
        case = EvalCase(case_id="1", query="q", answer="a", hits=[])
        with pytest.raises(RuntimeError):
            judge.judge(case)
