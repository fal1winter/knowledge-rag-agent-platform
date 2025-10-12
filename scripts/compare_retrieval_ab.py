#!/usr/bin/env python3
"""Compare benchmark result JSON files and compute target metric deltas."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def ratio_delta(new: float, old: float) -> float | None:
    if old == 0:
        return None
    return (new - old) / old


def latency_reduction(candidate_p99: float, baseline_p99: float) -> float | None:
    if baseline_p99 == 0:
        return None
    return (baseline_p99 - candidate_p99) / baseline_p99


def load_metrics(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def build_report(baseline: dict, candidate: dict) -> dict:
    recall_lift = ratio_delta(candidate.get("recall_at_k", 0.0), baseline.get("recall_at_k", 0.0))
    p99_reduction = latency_reduction(candidate.get("latency_ms_p99", 0.0), baseline.get("latency_ms_p99", 0.0))
    return {
        "recall_relative_lift": recall_lift,
        "intent_accuracy": candidate.get("intent_accuracy", 0.0),
        "intent_accuracy_delta": candidate.get("intent_accuracy", 0.0) - baseline.get("intent_accuracy", 0.0),
        "p99_latency_reduction": p99_reduction,
        "p99_latency_relative_delta": ratio_delta(
            candidate.get("latency_ms_p99", 0.0), baseline.get("latency_ms_p99", 0.0)
        ),
        "baseline": baseline,
        "candidate": candidate,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()

    report = build_report(load_metrics(args.baseline), load_metrics(args.candidate))
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(payload, encoding="utf-8")
    print(payload)


if __name__ == "__main__":
    main()
