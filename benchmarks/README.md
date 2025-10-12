# Benchmark Harness

This directory contains JSONL cases for retrieval, intent routing, grounded-answer checks, and latency measurement.

## Dataset schema

Each row in `*.jsonl` supports:

- `case_id`: stable case identifier.
- `tenant_id`, `user_id`: request scope.
- `query`: user question.
- `material_ids`: optional material filter.
- `expected_chunk_ids`: accepted chunk or document identifiers for recall@k.
- `expected_intent`: expected route value.
- `expected_facts`: facts used by the judge and rule evaluator.

## Produce raw metrics

```bash
PYTHONPATH=src python3 scripts/run_rag_benchmark.py \
  --dataset benchmarks/sample_eval.jsonl \
  --output artifacts/benchmarks/candidate.json \
  --top-k 8
```

The output includes `recall_at_k`, `intent_accuracy`, `latency_ms_p50`, `latency_ms_p99`, and judge scores.

## Compare baseline and candidate

```bash
python3 scripts/compare_retrieval_ab.py \
  --baseline artifacts/benchmarks/baseline.json \
  --candidate artifacts/benchmarks/candidate.json \
  --output artifacts/benchmarks/ab_report.json
```

The report emits `recall_relative_lift`, `intent_accuracy`, and `p99_latency_reduction`.

## Validate target metrics

```bash
python3 scripts/validate_target_metrics.py \
  --baseline artifacts/benchmarks/baseline.json \
  --candidate artifacts/benchmarks/candidate.json \
  --min-recall-lift 0.41 \
  --min-intent-accuracy 0.923 \
  --min-p99-reduction 0.42 \
  --output artifacts/benchmarks/target_report.json
```

The validator exits with code `0` only when all configured thresholds pass.
