# 评测基准工具

本目录包含用于检索召回、意图路由、答案质量检查和延迟测量的 JSONL 用例。

## 数据集 Schema

`*.jsonl` 中每行支持以下字段：

- `case_id`：稳定的用例标识。
- `tenant_id`、`user_id`：请求作用域。
- `query`：用户问题。
- `material_ids`：可选的素材过滤条件。
- `expected_chunk_ids`：recall@k 验收的目标 chunk 或文档标识。
- `expected_intent`：期望的意图路由值。
- `expected_facts`：供 Judge 和规则评测器使用的事实列表。

## 生成原始指标

```bash
PYTHONPATH=src python3 scripts/run_rag_benchmark.py \
  --dataset benchmarks/sample_eval.jsonl \
  --output artifacts/benchmarks/candidate.json \
  --top-k 8
```

输出包含 `recall_at_k`、`intent_accuracy`、`latency_ms_p50`、`latency_ms_p99` 及 Judge 评分。

## 对比 Baseline 与 Candidate

```bash
python3 scripts/compare_retrieval_ab.py \
  --baseline artifacts/benchmarks/baseline.json \
  --candidate artifacts/benchmarks/candidate.json \
  --output artifacts/benchmarks/ab_report.json
```

报告输出 `recall_relative_lift`、`intent_accuracy` 和 `p99_latency_reduction`。

## 验证目标指标

```bash
python3 scripts/validate_target_metrics.py \
  --baseline artifacts/benchmarks/baseline.json \
  --candidate artifacts/benchmarks/candidate.json \
  --min-recall-lift 0.41 \
  --min-intent-accuracy 0.923 \
  --min-p99-reduction 0.42 \
  --output artifacts/benchmarks/target_report.json
```

验证器仅在所有配置阈值均通过时以退出码 `0` 结束。
