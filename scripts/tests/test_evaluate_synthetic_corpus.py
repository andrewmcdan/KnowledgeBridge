"""Offline batch orchestration and real PDF reporting tests; never make paid requests."""

from contextlib import redirect_stdout, redirect_stderr
from copy import deepcopy
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
with patch.object(sys, "path", [str(SCRIPTS), *sys.path]):
    SPEC = importlib.util.spec_from_file_location("evaluate_synthetic_corpus", SCRIPTS / "evaluate_synthetic_corpus.py")
    corpus = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(corpus)

HAS_REPORTLAB = importlib.util.find_spec("reportlab") is not None
HAS_PYPDF = importlib.util.find_spec("pypdf") is not None


def fixture():
    plan = [{"case_id": f"ACME-001-q{i:02}", "doc_ids": ["ACME-001"], "query_number": i} for i in range(1, 7)]
    judgment = {
        "scores": {"accuracy": 4, "grounding": 4, "completeness": 4},
        "summary": "The answer covers the required facts.",
        "criteria": [{"index": 1, "verdict": "supported", "rationale": "The answer states the source fact."}],
        "unsupported_claims": [], "contradictions": [],
    }
    base = {"judgment_validated": True, "judgment": judgment,
            "generated": {"question": 'How should we handle <b>requests</b> & approvals?',
                          "criteria": [{"claim": "Approval must be recorded."}]},
            "synthesis": {"cacheHit": False}, "citation_check": {"all_selected_cited": True}}
    passed = {**deepcopy(base), **plan[0], "status": "pass"}
    failed = {**deepcopy(base), **plan[1], "status": "fail", "failures": ["Missing essential criterion."]}
    failed["judgment"].update({"scores": {"accuracy": 2, "grounding": 3, "completeness": 1},
                               "raw_scores": {"accuracy": 4, "grounding": 4, "completeness": 4},
                               "score_adjustments": ["Completeness capped for omitted criteria."]})
    failed["judgment"]["criteria"][0].update({"verdict": "missing", "rationale": "The required approval is omitted."})
    failed["synthesis"]["cacheHit"] = True
    error = {**deepcopy(base), **plan[2], "status": "error", "error": "Judge output invalid.", "stage": "judging",
             "judgment_validated": False}
    running = {**plan[3], "status": "running"}
    return {"report_format_version": 2, "run_id": "offline-fixture", "rubric_version": "synthesis-batch-v4",
            "status": "error", "mode": "per_document", "queries_per_doc": 6, "min_score": 3,
            "plan": plan, "cases": [passed, failed, error, running],
            "selected_documents": [{"doc_id": "ACME-001", "title": "Résumé - requests & approvals", "status": "current"}],
            "summary": {"passed": 999}, "models": {"generator": "fixture-generator", "judge": "fixture-judge"}}


def write_fixture(directory, report=None):
    source = directory / "results.json"
    source.write_text(json.dumps(report or fixture()), encoding="utf-8")
    return source


class CorpusTests(unittest.TestCase):
    def setUp(self):
        self.output = io.StringIO()

    def invoke(self, args):
        with redirect_stdout(self.output), redirect_stderr(self.output):
            return corpus.main(args)

    def test_entire_catalog_includes_noncurrent_sources_and_validates_checksums(self):
        ids = corpus.corpus_ids()
        catalog = json.loads((corpus.ROOT / "synthetic-data/reference/catalog.json").read_text(encoding="utf-8"))
        self.assertEqual(ids, [document["id"] for document in catalog["documents"]])
        self.assertEqual(len(ids), 50)
        self.assertTrue(any(document["status"] != "current" for document in catalog["documents"]))
        with patch.object(corpus.evaluator, "load_documents", side_effect=corpus.evaluator.EvaluationError("checksum differs")):
            with self.assertRaisesRegex(corpus.evaluator.EvaluationError, "checksum differs"):
                corpus.corpus_ids()

    def test_aggregates_recomputed_errors_and_incomplete_cases_excluded_from_scores(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = corpus.load_report(write_fixture(Path(temporary)))
        summary = report["summary"]
        self.assertEqual([summary[key] for key in ["planned", "passed", "failed", "errors", "not_run", "scored_cases"]],
                         [6, 1, 1, 1, 3, 2])
        self.assertAlmostEqual(summary["pass_rate"], 1 / 6)
        self.assertEqual(summary["average_scores"], {"accuracy": 3, "grounding": 3.5, "completeness": 2.5})
        self.assertEqual(corpus.case_scores(report["cases"][2]), ["N/A"] * 3)
        self.assertEqual(corpus.case_scores(report["cases"][3]), ["N/A"] * 3)

    def test_malformed_reports_are_rejected_instead_of_printing_misleading_results(self):
        reports = []
        for invalid_score in [True, 5, -1, "4", None]:
            report = fixture()
            report["cases"][0]["judgment"]["scores"]["accuracy"] = invalid_score
            reports.append(report)
        report = fixture()
        report["cases"].append(deepcopy(report["cases"][0]))
        reports.append(report)
        report = fixture()
        report["cases"][0]["case_id"] = "unknown"
        reports.append(report)
        report = fixture()
        report["cases"][0]["judgment_validated"] = False
        reports.append(report)
        reports.append({"report_format_version": 1})
        for report in reports:
            with self.subTest(report=report), tempfile.TemporaryDirectory() as temporary:
                with self.assertRaises(corpus.evaluator.EvaluationError):
                    corpus.load_report(write_fixture(Path(temporary), report))

    def test_missing_pdf_dependency_fails_before_evaluator_or_authentication(self):
        with patch.object(corpus, "pdf_dependencies", side_effect=corpus.evaluator.EvaluationError("Install ReportLab")), \
                patch.object(corpus.evaluator, "main") as evaluate:
            self.assertEqual(self.invoke([]), 2)
            evaluate.assert_not_called()
        self.assertIn("Install ReportLab", self.output.getvalue())

    @unittest.skipUnless(HAS_REPORTLAB, "Install scripts/requirements-evaluation.txt for PDF tests")
    def test_dry_run_plans_every_query_without_authentication_and_writes_pdf_and_json(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(corpus.evaluator, "Application") as app, \
                patch.object(corpus.evaluator, "EvaluatorAI") as ai:
            directory = Path(temporary)
            self.assertEqual(self.invoke(["--dry-run", "--queries-per-doc", "2", "--output-dir", str(directory)]), 0)
            app.assert_not_called()
            ai.assert_not_called()
            report = corpus.load_report(directory / "results.json")
            self.assertEqual(len(report["plan"]), 100)
            self.assertEqual(report["summary"]["not_run"], 100)
            self.assertEqual(report["status"], "dry_run")
            self.assertEqual((directory / "report.pdf").read_bytes()[:5], b"%PDF-")

    @unittest.skipUnless(HAS_REPORTLAB, "Install scripts/requirements-evaluation.txt for PDF tests")
    def test_delegates_all_ids_and_options_preserves_failure_codes_and_reports_errors(self):
        for code in [0, 1, 2]:
            with self.subTest(code=code), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)

                def evaluate(arguments):
                    source = Path(arguments[arguments.index("--output") + 1])
                    write_fixture(source.parent)
                    self.assertEqual(arguments.count("--doc-id"), 50)
                    self.assertIn("ACME-018", arguments)
                    self.assertEqual(arguments[arguments.index("--queries-per-doc") + 1], "4")
                    for option in ["--require-fresh", "--generator-model", "--judge-model", "--min-score", "--env-file"]:
                        self.assertIn(option, arguments)
                    return code

                with patch.object(corpus.evaluator, "main", side_effect=evaluate):
                    self.assertEqual(self.invoke(["--output-dir", str(directory), "--queries-per-doc", "4",
                                                  "--require-fresh", "--generator-model", "generator", "--judge-model", "judge",
                                                  "--min-score", "4", "--env-file", "custom.env"]), code)
                self.assertTrue((directory / "report.pdf").is_file())

    @unittest.skipUnless(HAS_REPORTLAB, "Install scripts/requirements-evaluation.txt for PDF tests")
    def test_configuration_failure_still_creates_pdf_without_scores(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(corpus.evaluator, "read_env", return_value={}):
            directory = Path(temporary)
            self.assertEqual(self.invoke(["--output-dir", str(directory)]), 2)
            report = corpus.load_report(directory / "results.json")
            self.assertEqual(report["summary"]["scored_cases"], 0)
            self.assertEqual(report["summary"]["not_run"], 50)
            self.assertTrue((directory / "report.pdf").is_file())

    @unittest.skipUnless(HAS_REPORTLAB, "Install scripts/requirements-evaluation.txt for PDF tests")
    def test_pdf_failure_preserves_the_json_for_report_only_recovery(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = write_fixture(Path(temporary))
            original = source.read_bytes()
            with patch.object(corpus, "build_pdf", side_effect=RuntimeError("Layout failed")):
                self.assertEqual(self.invoke(["--from-json", str(source)]), 2)
            self.assertEqual(source.read_bytes(), original)
            self.assertIn("PDF generation failed (RuntimeError)", self.output.getvalue())
            self.assertFalse(source.with_suffix(".pdf").exists())

    @unittest.skipUnless(HAS_REPORTLAB, "Install scripts/requirements-evaluation.txt for PDF tests")
    def test_report_only_never_repeats_calls_or_overwrites_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(corpus.evaluator, "main") as evaluate:
            directory = Path(temporary)
            source = write_fixture(directory)
            before = source.read_bytes()
            self.assertEqual(self.invoke(["--from-json", str(source)]), 0)
            pdf_before = source.with_suffix(".pdf").read_bytes()
            self.assertEqual(self.invoke(["--from-json", str(source)]), 2)
            self.assertEqual(pdf_before, source.with_suffix(".pdf").read_bytes())
            self.assertEqual(source.read_bytes(), before)
            self.assertEqual(self.invoke(["--output-dir", str(directory)]), 2)
            evaluate.assert_not_called()

    @unittest.skipUnless(HAS_REPORTLAB and HAS_PYPDF, "ReportLab and optional pypdf are needed for PDF text checks")
    def test_real_pdf_contains_all_planned_scores_literal_markup_reasons_and_pagination(self):
        from pypdf import PdfReader
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = write_fixture(directory)
            report = corpus.load_report(source)
            # Exercise multi-page paragraphs and a long unbroken title in a table cell.
            report["cases"][1]["judgment"]["summary"] = "A long explanation. " * 1000
            report["selected_documents"][0]["title"] += " X" + "y" * 500
            output = directory / "report.pdf"
            corpus.build_pdf(report, source, output)
            pdf = PdfReader(output)
            text = "\n".join(page.extract_text() for page in pdf.pages)
            self.assertGreater(len(pdf.pages), 3)
            for expected in ["16.7%", "3.00", "3.50", "2.50", "N/A", "NOT_RUN", "RUNNING", "ACME-001-q06",
                             "<b>requests</b> & approvals", "Judge output invalid.", "Missing essential criterion.",
                             "Score adjustment:", "Résumé", "Page 1"]:
                self.assertIn(expected, text)


if __name__ == "__main__":
    unittest.main()
