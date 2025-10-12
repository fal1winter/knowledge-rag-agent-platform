#!/usr/bin/env python3
"""Validate retrieval, routing, and latency metrics against configured targets."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def ratio_delta(new: float, old: float) -> float | None:
    if old == 0:
        return None
    return (new - old) / old


def latency_reduction(candidate_p99: float, baseline_p99: float) -> float | None:
    if baseline_p99 == 0:
        return None
    return (baseline_p99 - candidate_p99) / baseline_p99


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, help="metrics JSON from the baseline pipeline")
    parser.add_argument("--candidate", required=True, help="metrics JSON from the candidate pipeline")
    parser.add_argument("--min-recall-lift", type=float, default=0.41)
    parser.add_argument("--min-intent-accuracy", type=float, default=0.923)
    parser.add_argument("--min-p99-reduction", type=float, default=0.42)
    parser.add_argument("--output")
    args = parser.parse_args()

    baseline = load(args.baseline)
    candidate = load(args.candidate)
    recall_lift = ratio_delta(candidate.get("recall_at_k", 0.0), baseline.get("recall_at_k", 0.0))
    p99_reduction = latency_reduction(candidate.get("latency_ms_p99", 0.0), baseline.get("latency_ms_p99", 0.0))
    intent_accuracy = candidate.get("intent_accuracy", 0.0)

    checks = {
        "recall_lift_target": recall_lift is not None and recall_lift >= args.min_recall_lift,
        "intent_accuracy_target": intent_accuracy >= args.min_intent_accuracy,
        "p99_reduction_target": p99_reduction is not None and p99_reduction >= args.min_p99_reduction,
    }
    report = {
        "recall_relative_lift": recall_lift,
        "intent_accuracy": intent_accuracy,
        "p99_latency_reduction": p99_reduction,
        "targets": {
            "min_recall_lift": args.min_recall_lift,
            "min_intent_accuracy": args.min_intent_accuracy,
            "min_p99_reduction": args.min_p99_reduction,
        },
        "checks": checks,
        "passed": all(checks.values()),
        "baseline": baseline,
        "candidate": candidate,
    }
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(payload, encoding="utf-8")
    print(payload)
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
