#!/usr/bin/env python3
"""Run retrieval, routing, judge, and latency benchmarks from a JSONL dataset."""

from __future__ import annotations

import argparse
import json
import statistics
import time
from pathlib import Path

from rag_agent_platform.bootstrap import build_runtime
from rag_agent_platform.evaluation.llm_judge import LLMJudge
from rag_agent_platform.evaluation.rules import RuleBasedEvaluator
from rag_agent_platform.models import EvalCase, QueryRequest


def load_jsonl(path: str):
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                yield json.loads(line)


def recall_at_k(hits, expected_ids, k):
    if not expected_ids:
        return 0.0
    got = {hit.chunk_id for hit in hits[:k]} | {hit.document_id for hit in hits[:k]}
    return len(got & set(expected_ids)) / len(set(expected_ids))


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))
    return ordered[index]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dataset', required=True, help='JSONL with query, expected_chunk_ids, expected_intent, expected_facts')
    parser.add_argument('--output', required=True)
    parser.add_argument('--top-k', type=int, default=8)
    args = parser.parse_args()

    agent, _ = build_runtime()
    rules = RuleBasedEvaluator()
    judge = LLMJudge()
    rows = list(load_jsonl(args.dataset))
    recalls = []
    route_correct = 0
    latencies_ms = []
    judge_scores = []

    for idx, row in enumerate(rows):
        request = QueryRequest(
            tenant_id=row.get('tenant_id', 'tenant-a'),
            user_id=row.get('user_id', 'benchmark'),
            message=row['query'],
            material_ids=row.get('material_ids'),
        )
        started = time.perf_counter()
        answer = agent.handle(request)
        latencies_ms.append((time.perf_counter() - started) * 1000)
        expected_ids = row.get('expected_chunk_ids', [])
        recalls.append(recall_at_k(answer.retrieval_hits, expected_ids, args.top_k))
        if row.get('expected_intent') and answer.route.intent.value == row['expected_intent']:
            route_correct += 1
        case = EvalCase(
            case_id=str(row.get('case_id', idx)),
            query=row['query'],
            answer=answer.text,
            hits=answer.retrieval_hits,
            expected_facts=row.get('expected_facts', []),
        )
        issues = rules.evaluate(case)
        judge_scores.append(judge.judge(case, issues).__dict__)

    metrics = {
        'cases': len(rows),
        'recall_at_k': statistics.mean(recalls) if recalls else 0.0,
        'intent_accuracy': route_correct / len(rows) if rows else 0.0,
        'latency_ms_p50': percentile(latencies_ms, 50),
        'latency_ms_p99': percentile(latencies_ms, 99),
        'judge_scores': judge_scores,
    }
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(metrics, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
