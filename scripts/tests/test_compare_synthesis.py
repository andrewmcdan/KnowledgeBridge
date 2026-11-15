"""Fixed-suite and paired comparison checks; offline, including real PDF generation."""

from contextlib import redirect_stdout, redirect_stderr
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

with patch.object(sys, "path", [str(Path(__file__).resolve().parents[1]), *sys.path]):
    import compare_synthesis as comparison


class ComparisonTests(unittest.TestCase):
    def test_reviewed_twenty_case_suite_has_canonical_quotes_and_correct_retry_contract(self):
        date, documents, plan = comparison.evaluator.load_suite(comparison.ROOT / "evaluation/diagnostic-suite.json")
        self.assertEqual(date, "2026-09-11")
        self.assertEqual(len(plan), 20)
        self.assertGreater(len(documents), 10)
        self.assertEqual({case["category"] for case in plan}, {"facts", "procedure", "boundary", "historical", "conflict", "gap"})
        retry = next(case for case in plan if case["case_id"] == "DIAG-13")
        self.assertIn("four retries", retry["benchmark"]["expected_answer"])
        self.assertTrue(all(len(case["benchmark"]["question"]) < 500 for case in plan))

    def test_deltas_are_only_calculated_for_identical_context_and_validated_judgments(self):
        def arm(model, scope, context):
            return {"model": model, "scope": scope, "report": {"cases": [{"case_id": "Q1", "status": "pass",
                "retrieval_trace": {"context_sha256": context}, "judgment_validated": True,
                "judgment": {"scores": {"accuracy": 4, "grounding": 4, "completeness": 4}}}]}}
        first = arm("A", "corpus", "same")
        self.assertEqual(comparison.paired_results([first, arm("B", "workspace", "same")]), [])
        changed = comparison.paired_results([first, arm("B", "corpus", "changed")])[0]
        self.assertFalse(changed["context_matched"])
        self.assertIsNone(changed["score_delta_right_minus_left"])
        matched = comparison.paired_results([first, arm("B", "corpus", "same")])[0]
        self.assertEqual(matched["score_delta_right_minus_left"], {"accuracy": 0, "grounding": 0, "completeness": 0})

    def test_all_error_arm_stops_later_paid_arms_and_preserves_checkpoint(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(comparison, "pdf_dependencies"), \
                patch.object(comparison, "build_pdf"), \
                patch.object(comparison.evaluator, "main", return_value=2) as run, \
                patch.object(comparison, "load_report", return_value={
                    "summary": {"completed": 1, "errors": 1},
                    "cases": [{"case_id": "DIAG-01", "status": "error"}],
                }), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            directory = Path(temporary) / "comparison"
            self.assertEqual(comparison.main(["--model", "A", "--model", "B", "--output-dir", str(directory)]), 2)
            self.assertEqual(run.call_count, 1)
            report = json.loads((directory / "comparison.json").read_text())
            self.assertEqual(report["status"], "error")
            self.assertEqual(len(report["arms"]), 1)
            self.assertTrue((directory / "suite.json").is_file())

    @unittest.skipUnless(importlib.util.find_spec("reportlab"), "ReportLab is required for PDF checks")
    def test_dry_comparison_freezes_suite_and_writes_four_arms_without_auth_or_ai(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(comparison.evaluator, "Application") as app, \
                patch.object(comparison.evaluator, "EvaluatorAI") as ai, \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            directory = Path(temporary) / "comparison"
            self.assertEqual(comparison.main(["--model", "A", "--model", "B", "--dry-run", "--output-dir", str(directory)]), 0)
            app.assert_not_called()
            ai.assert_not_called()
            report = json.loads((directory / "comparison.json").read_text())
            self.assertEqual(report["status"], "dry_run")
            self.assertEqual(len(report["arms"]), 4)
            self.assertTrue(all(arm["summary"]["planned"] == 20 for arm in report["arms"]))
            self.assertEqual(len(list(directory.glob("*.pdf"))), 4)
            self.assertEqual((directory / "suite.json").read_bytes(), (comparison.ROOT / "evaluation/diagnostic-suite.json").read_bytes())


if __name__ == "__main__":
    unittest.main()
