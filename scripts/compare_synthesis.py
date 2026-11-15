"""Run a fixed diagnostic suite across synthesis models and corpus/workspace evidence scopes."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import uuid

import evaluate_synthesis as evaluator
from evaluate_synthetic_corpus import build_pdf, load_report, pdf_dependencies

ROOT = Path(__file__).resolve().parents[1]


def paired_results(arms):
    """Only attribute a difference to a model when the exact prompts were identical."""
    pairs = []
    for index, left in enumerate(arms):
        for right in arms[index + 1:]:
            if left["scope"] != right["scope"]:
                continue
            by_id = {case["case_id"]: case for case in right["report"]["cases"]}
            for first in left["report"]["cases"]:
                second = by_id.get(first["case_id"])
                if second is None:
                    continue
                left_hash = first.get("retrieval_trace", {}).get("context_sha256")
                right_hash = second.get("retrieval_trace", {}).get("context_sha256")
                matched = bool(left_hash and left_hash == right_hash)
                scored = (first.get("judgment_validated") and second.get("judgment_validated")
                          and first["status"] in {"pass", "fail"} and second["status"] in {"pass", "fail"})
                pairs.append({"case_id": first["case_id"], "scope": left["scope"],
                              "left_model": left["model"], "right_model": right["model"],
                              "context_matched": matched,
                              "score_delta_right_minus_left": {
                                  key: second["judgment"]["scores"][key] - first["judgment"]["scores"][key]
                                  for key in ["accuracy", "grounding", "completeness"]
                              } if matched and scored else None,
                              "qualification": "matched prompts" if matched else "no model-effect conclusion: context differs or was not recorded"})
    return pairs


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", type=Path, default=ROOT / "evaluation/diagnostic-suite.json")
    parser.add_argument("--model", action="append", required=True, help="Allowed synthesis model; repeat for matched comparisons.")
    parser.add_argument("--scope", choices=["corpus", "workspace"], action="append", help="Default: both scopes.")
    parser.add_argument("--case-id", action="append", help="Fixed-suite subset; repeat for selected cases.")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    parser.add_argument("--judge-model")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    try:
        pdf_dependencies()
        evaluator.load_suite(args.suite)
        if len(set(args.model)) != len(args.model):
            raise evaluator.EvaluationError("Comparison models must be distinct.")
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        directory = args.output_dir or ROOT / "evaluation/runs" / ("comparison-" + run_id)
        directory.mkdir(parents=True, exist_ok=False)
        frozen = directory / "suite.json"
        frozen.write_bytes(args.suite.read_bytes())
        summary = {"run_id": run_id, "status": "running", "dry_run": args.dry_run,
                   "suite_sha256": hashlib.sha256(frozen.read_bytes()).hexdigest(), "arms": [], "pairs": []}
        evaluator.write_report(directory / "comparison.json", summary)
        arms, codes = [], []
        for scope in dict.fromkeys(args.scope or ["corpus", "workspace"]):
            for index, model in enumerate(args.model, 1):
                name = f"{scope}-model-{index}"
                output = directory / (name + ".json")
                command = ["--suite", str(frozen), "--output", str(output), "--api-url", args.api_url,
                           "--env-file", str(args.env_file), "--evaluation-model", model]
                if scope == "corpus":
                    command.append("--corpus-only")
                if args.dry_run:
                    command.append("--dry-run")
                if args.judge_model:
                    command.extend(["--judge-model", args.judge_model])
                for case_id in args.case_id or []:
                    command.extend(["--case-id", case_id])
                print(f"\nARM: {name}; model={model}; no cache/retry, exact fixed questions", flush=True)
                code = evaluator.main(command)
                codes.append(code)
                report = load_report(output)
                arms.append({"scope": scope, "model": model, "report": report})
                build_pdf(report, output, directory / (name + ".pdf"))
                summary["arms"].append({"scope": scope, "model": model, "json": output.name,
                                        "exit_code": code, "summary": report["summary"]})
                summary["pairs"] = paired_results(arms)
                evaluator.write_report(directory / "comparison.json", summary)
                all_errors = report["summary"]["completed"] > 0 and report["summary"]["errors"] == report["summary"]["completed"]
                if report.get("error") or (all_errors and not args.dry_run) or any("interrupted" in case.get("error", "").lower() for case in report["cases"]):
                    summary["status"] = "error"
                    summary["error"] = "Stopped after a batch-wide failure, all-error arm or interruption. No later arms were started."
                    evaluator.write_report(directory / "comparison.json", summary)
                    raise evaluator.EvaluationError("Comparison stopped after a batch-wide error/interruption; earlier arms are preserved.")
        summary["status"] = "dry_run" if args.dry_run else "complete"
        summary["finished_at"] = datetime.now(timezone.utc).isoformat()
        evaluator.write_report(directory / "comparison.json", summary)
        print(f"Comparison: {directory / 'comparison.json'}")
        return max(codes, default=0)
    except (evaluator.EvaluationError, OSError, ValueError, KeyError, TypeError) as error:
        print(f"Comparison failed: {error}. Earlier JSON/PDF artifacts are preserved.", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Comparison interrupted; no new arms will start. Saved checkpoints are preserved.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
