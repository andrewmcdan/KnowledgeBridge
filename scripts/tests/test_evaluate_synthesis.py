"""Offline tests only: no real provider, synthesis, credentials, or database calls."""

from contextlib import redirect_stdout, redirect_stderr
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
from threading import Thread
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("evaluate_synthesis", Path(__file__).parents[1] / "evaluate_synthesis.py")
evaluation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evaluation)

BODY_ONE = "Production access uses company SSO and MFA. Elevated production access lasts at most four hours per grant."
BODY_TWO = "Finance reviews business purpose and cost-center coding. Submit expense claims within thirty days after travel ends."
ANSWER = "Use company SSO and MFA. Finance reviews purpose and cost-center coding."
SELECTED = [{"doc_id": "ACME-013", "text": BODY_ONE}, {"doc_id": "ACME-017", "text": BODY_TWO}]


def generated(selected=SELECTED):
    return {"question": "What are the production access rules and how are travel expenses reviewed?", "expected_answer": "Use SSO and MFA; Finance reviews purpose and cost center.",
            "criteria": [{"doc_id": doc["doc_id"], "claim": "State the required control.", "quote": doc["text"]} for doc in selected]}


def judgment(selected=SELECTED):
    return {"scores": {"accuracy": 4, "grounding": 4, "completeness": 4}, "summary": "All required facts are supported.",
            "unsupported_claims": [], "contradictions": [], "criteria": [
                {"index": i + 1, "verdict": "supported", "rationale": "The answer states this source fact.",
                 "answer_quote": ANSWER,
                 "evidence": [{"item_id": (i + 1) * 10, "quote": doc["text"]}]} for i, doc in enumerate(selected)]}


class FakeApp:
    def __init__(self):
        self.base = "http://127.0.0.1:8080"
        self.bodies = {10: BODY_ONE, 11: BODY_ONE, 20: BODY_TWO}
        self.synthesis_calls = []
        self.response = {"answer": ANSWER,
                         "aiGenerated": True, "extractiveFallback": False, "cacheHit": False,
                         "citations": [{"itemKey": str(i), "externalId": f"knowledgebridge/{i}"} for i in [10, 20]]}
        self.changed = False

    def request(self, path, payload=None):
        if path == "/api/knowledge-items":
            return [{"id": i, "status": "COMPLETED"} for i in self.bodies]
        assert path == "/api/query/synthesize", path
        self.synthesis_calls.append(payload)
        return self.response

    def content(self, item_id, fresh=False):
        return {"id": item_id, "title": "Renamed upload", "text": "A changed body" if fresh and self.changed else self.bodies[item_id]}


class FakeAI:
    def __init__(self):
        self.generated = generated()
        self.judgment = judgment()
        self.requests = []
        self.schemas = []
        self.calls = []

    def ask(self, stage, instruction, inputs, schema):
        self.requests.append((stage, inputs))
        self.schemas.append(schema)
        return self.generated if stage == "generation" else self.judgment


class EvaluationTests(unittest.TestCase):
    def run_case(self, app=None, ai=None, **options):
        app, ai, report = app or FakeApp(), ai or FakeAI(), {}
        with redirect_stdout(io.StringIO()):
            code = evaluation.run_evaluation(app, ai, SELECTED, "2026-09-11", report, **options)
        return code, report, app, ai

    def test_one_shot_maps_renamed_and_duplicate_uploads_and_judges_actual_cited_bodies(self):
        code, report, app, ai = self.run_case()
        self.assertEqual(code, 0)
        self.assertEqual(report["status"], "pass")
        self.assertEqual(report["source_item_ids"], {"ACME-013": [10, 11], "ACME-017": [20]})
        self.assertEqual(app.synthesis_calls, [{"question": ai.generated["question"]}])
        self.assertEqual([stage for stage, _ in ai.requests], ["generation", "judging"])
        self.assertEqual(ai.requests[1][1]["cited_documents"][0]["text"], BODY_ONE)
        self.assertEqual(ai.requests[1][1]["candidate_answer"], app.response["answer"])
        self.assertNotIn("expected_answer", ai.requests[1][1])
        quotes = ai.schemas[1]["properties"]["criteria"]["items"]["properties"]["answer_quote"]["enum"]
        self.assertEqual(quotes, ["", *ai.requests[1][1]["candidate_answer_passages"]])
        self.assertIn("body_sha256", report["cited_documents"][0])

    def test_all_selected_sources_are_required_even_if_judge_awards_perfect_scores(self):
        app, ai = FakeApp(), FakeAI()
        app.response["citations"] = [{"itemKey": "10", "externalId": "knowledgebridge/10"}]
        ai.judgment["criteria"][1].update({"verdict": "missing", "evidence": []})
        code, report, _, _ = self.run_case(app, ai)
        self.assertEqual(code, 1)
        self.assertEqual(report["citation_check"]["missing_doc_ids"], ["ACME-017"])
        self.assertEqual(report["status"], "fail")

    def test_quality_threshold_unsupported_claims_and_missing_criteria_fail(self):
        for change in ["score", "unsupported", "contradiction", "missing"]:
            with self.subTest(change=change):
                ai = FakeAI()
                if change == "score": ai.judgment["scores"]["accuracy"] = 2
                if change == "unsupported": ai.judgment["unsupported_claims"] = ["Invented SLA"]
                if change == "contradiction": ai.judgment["contradictions"] = ["Wrong threshold"]
                if change == "missing": ai.judgment["criteria"][0]["verdict"] = "missing"
                self.assertEqual(self.run_case(ai=ai)[0], 1)

    def test_extractive_fallback_and_required_fresh_cache_are_not_passes(self):
        app = FakeApp()
        app.response.update({"aiGenerated": False, "extractiveFallback": True})
        self.assertEqual(self.run_case(app=app)[0], 1)
        app = FakeApp()
        app.response["cacheHit"] = True
        self.assertEqual(self.run_case(app=app)[0], 0)
        self.assertEqual(self.run_case(app=app, require_fresh=True)[0], 1)

    def test_missing_ingestion_fails_before_paid_calls_and_changed_source_is_inconclusive(self):
        app, ai = FakeApp(), FakeAI()
        del app.bodies[20]
        with self.assertRaisesRegex(evaluation.EvaluationError, "not ingested"):
            self.run_case(app, ai)
        self.assertEqual(ai.requests, [])
        self.assertEqual(app.synthesis_calls, [])
        app = FakeApp()
        app.changed = True
        with self.assertRaisesRegex(evaluation.EvaluationError, "changed"):
            self.run_case(app)

    def test_generated_quotes_and_every_selected_source_are_validated(self):
        for kind in ["invented", "missing", "unselected", "bad_id", "leaked", "nul"]:
            value = generated()
            if kind == "invented": value["criteria"][0]["quote"] = "A nonexistent source policy about finance."
            if kind == "missing": value["criteria"].pop()
            if kind == "unselected": value["criteria"][0]["doc_id"] = "ACME-999"
            if kind == "bad_id": value["criteria"][0]["doc_id"] = []
            if kind == "leaked": value["question"] = "What does ACME-013 say?"
            if kind == "nul": value["question"] += "\0"
            with self.subTest(kind=kind), self.assertRaises(evaluation.EvaluationError):
                evaluation.validate_generation(value, SELECTED)

    def test_judge_requires_full_criterion_coverage_real_quotes_and_valid_scores(self):
        cited = [FakeApp().content(i) for i in [10, 20]]
        for kind in ["quote", "id", "empty", "duplicate", "missing", "bool_score", "verdict"]:
            value = judgment()
            if kind == "quote": value["criteria"][0]["evidence"][0]["quote"] = "An invented supporting quote that does not exist."
            if kind == "id": value["criteria"][0]["evidence"][0]["item_id"] = 999
            if kind == "empty": value["criteria"][0]["evidence"] = []
            if kind == "duplicate": value["criteria"][1]["index"] = 1
            if kind == "missing": value["criteria"].pop()
            if kind == "bool_score": value["scores"]["accuracy"] = True
            if kind == "verdict": value["criteria"][0]["verdict"] = {}
            with self.subTest(kind=kind), self.assertRaises(evaluation.EvaluationError):
                evaluation.validate_judgment(value, generated(), cited, ANSWER)

    def test_judge_cannot_substitute_a_source_quote_for_an_omitted_answer_fact(self):
        cited = [FakeApp().content(i) for i in [10, 20]]
        for quote in ["", None, "Elevated production access lasts at most four hours per grant."]:
            value = judgment()
            value["criteria"][0]["answer_quote"] = quote
            with self.subTest(quote=quote), self.assertRaises(evaluation.EvaluationError):
                evaluation.validate_judgment(value, generated(), cited, ANSWER)
        value["criteria"][0].update({"verdict": "missing", "answer_quote": "", "evidence": []})
        evaluation.validate_judgment(value, generated(), cited, ANSWER)

    def test_judge_schema_constrains_answer_quotes_to_actual_passages(self):
        answer = "# Access rules\n\nUse **SSO** and MFA [knowledgebridge/10].\nKeep the approval ticket.\n\n" + "x" * 8001
        passages = evaluation.answer_passages(answer)
        schema = evaluation.judge_schema(passages)
        choices = schema["properties"]["criteria"]["items"]["properties"]["answer_quote"]["enum"]
        self.assertEqual(choices, ["", *passages])
        self.assertTrue(all(passage in answer for passage in passages))
        self.assertTrue(all(0 < len(passage) <= 8000 for passage in passages))
        self.assertEqual("".join(passages[-2:]), "x" * 8001)
        self.assertNotIn("enum", evaluation.JUDGE_SCHEMA["properties"]["criteria"]["items"]["properties"]["answer_quote"])

    def test_reported_fabricated_credential_quote_is_inconclusive_even_with_passing_scores(self):
        ai = FakeAI()
        ai.judgment["scores"] = {name: 3 for name in ["accuracy", "grounding", "completeness"]}
        ai.judgment["criteria"][0]["answer_quote"] = "Never copy production credentials or recovery codes into tickets, documents, or knowledge-base uploads."
        with self.assertRaisesRegex(evaluation.EvaluationError, "criterion 1.*absent.*inconclusive"):
            self.run_case(ai=ai)

    def test_provider_cannot_ignore_the_answer_passage_enum(self):
        ai = FakeAI()
        ai.judgment["criteria"][0]["answer_quote"] = "Use company SSO and MFA."
        with self.assertRaisesRegex(evaluation.EvaluationError, "allowed answer passage"):
            self.run_case(ai=ai)

    def test_citation_identifiers_are_consistent_and_positive(self):
        self.assertEqual(evaluation.citation_id({"itemKey": "010", "externalId": "knowledgebridge/10"}), 10)
        for citation in [{}, {"itemKey": "0"}, {"itemKey": "-1"}, {"itemKey": "10", "externalId": "knowledgebridge/20"}, {"itemKey": "10", "externalId": 10}]:
            with self.subTest(citation=citation), self.assertRaises(evaluation.EvaluationError): evaluation.citation_id(citation)

    def test_context_limits_fail_instead_of_silently_truncating(self):
        app, ai = FakeApp(), FakeAI()
        with self.assertRaisesRegex(evaluation.EvaluationError, "context"):
            self.run_case(app, ai, max_context=10)
        self.assertEqual([stage for stage, _ in ai.requests], ["generation"])

    def test_seeded_selection_and_explicit_multiple_documents(self):
        _, first = evaluation.load_documents(None, 2, 42)
        _, second = evaluation.load_documents(None, 2, 42)
        self.assertEqual(first, second)
        self.assertTrue(all(doc["status"] == "current" for doc in first))
        _, docs = evaluation.load_documents(["ACME-011", "ACME-012"], 1, 42)
        self.assertEqual([doc["doc_id"] for doc in docs], ["ACME-011", "ACME-012"])
        for ids in [["ACME-999"], ["ACME-011", "ACME-011"]]:
            with self.assertRaises(evaluation.EvaluationError): evaluation.load_documents(ids, 1, 42)

    def test_env_is_plain_data_and_shell_overrides_without_exposing_keys(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / ".env"
            path.write_text("OPENROUTER_API_KEY='file-key'\nLITERAL=$(never_execute)\nCOMMENT=value # ignored\n", encoding="utf-8")
            with patch.dict(os.environ, {"OPENROUTER_API_KEY": "shell-key"}, clear=True):
                env = evaluation.read_env(path)
            self.assertEqual(env["OPENROUTER_API_KEY"], "shell-key")
            self.assertEqual(env["LITERAL"], "$(never_execute)")
            self.assertEqual(env["COMMENT"], "value")

    def test_ai_requests_use_schema_and_separate_conversations_and_reject_truncation(self):
        calls = []
        def transport(url, method, body, headers):
            calls.append((url, body, headers))
            return {"choices": [{"finish_reason": "stop", "message": {"content": json.dumps(generated())}}], "usage": {"total_tokens": 50}}
        ai = evaluation.EvaluatorAI({"OPENROUTER_API_KEY": "secret"}, "generator", "judge", transport)
        ai.ask("generation", "Generate", {}, evaluation.GENERATION_SCHEMA)
        ai.ask("judging", "Judge", {}, evaluation.JUDGE_SCHEMA)
        self.assertEqual([call[1]["model"] for call in calls], ["generator", "judge"])
        self.assertEqual(len(calls[1][1]["messages"]), 2)
        self.assertEqual(calls[0][1]["response_format"]["type"], "json_schema")
        self.assertNotIn("secret", json.dumps(ai.calls))
        ai.transport = lambda *args: {"choices": [{"finish_reason": "length"}]}
        with self.assertRaisesRegex(evaluation.EvaluationError, "finish normally"):
            ai.ask("generation", "Generate", {}, evaluation.GENERATION_SCHEMA)

    def test_dry_run_and_existing_output_make_no_http_calls(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "report.json"
            with patch.object(evaluation, "http_json", side_effect=AssertionError("No network")), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(evaluation.main(["--dry-run", "--doc-id", "ACME-013", "--output", str(path)]), 0)
                self.assertEqual(json.loads(path.read_text())["status"], "dry_run")
                self.assertEqual(evaluation.main(["--doc-id", "ACME-013", "--output", str(path)]), 2)

    def test_missing_credentials_write_an_error_report_without_secrets(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "report.json"
            with patch.dict(os.environ, {}, clear=True), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                code = evaluation.main(["--doc-id", "ACME-013", "--env-file", str(Path(folder) / "absent"), "--output", str(path)])
            self.assertEqual(code, 2)
            self.assertEqual(json.loads(path.read_text())["status"], "error")

    def test_remote_plaintext_and_credential_bearing_api_urls_are_rejected(self):
        for url in ["http://remote.example", "https://user:password@example.test", "https://example.test?token=x", "https://example.test/api"]:
            with self.assertRaises(evaluation.EvaluationError): evaluation.validate_api_url(url)

    def test_application_login_bearer_content_cache_and_fresh_reads_use_spring_only(self):
        requests = []
        def transport(url, method, payload, headers=None):
            requests.append((url, method, payload, headers))
            if url.endswith("/api/auth/login"):
                return {"token": "private-token"}
            self.assertTrue(url.endswith("/api/knowledge-items/10/content"))
            return {"id": 10, "title": "Source", "text": BODY_ONE}
        app = evaluation.Application("http://127.0.0.1:8080", {"KB_EVAL_EMAIL": "test@example.test", "KB_EVAL_PASSWORD": "private-password"}, transport)
        app.content(10)
        app.content(10)
        app.content(10, fresh=True)
        self.assertEqual(len(requests), 3)
        self.assertEqual(requests[0][2], {"email": "test@example.test", "password": "private-password"})
        self.assertEqual(requests[1][3], {"Authorization": "Bearer private-token"})
        requests.clear()
        evaluation.Application("http://127.0.0.1:8080", {"KB_EVAL_TOKEN": "existing-token"}, transport)
        self.assertEqual(requests, [])

    def test_command_writes_a_complete_pass_report_or_an_inconclusive_error_report(self):
        for invalid in [False, True]:
            with self.subTest(invalid=invalid), tempfile.TemporaryDirectory() as folder:
                ai = FakeAI()
                if invalid:
                    ai.judgment["criteria"][0]["evidence"][0]["quote"] = "A fake evidence quote that does not exist."
                path = Path(folder) / "run.json"
                stdout = io.StringIO()
                with patch.object(evaluation, "load_documents", return_value=("2026-09-11", SELECTED)), patch.object(evaluation, "Application", return_value=FakeApp()), patch.object(evaluation, "EvaluatorAI", return_value=ai), redirect_stdout(stdout), redirect_stderr(io.StringIO()):
                    code = evaluation.main(["--doc-id", "ACME-013", "--output", str(path)])
                report = json.loads(path.read_text())
                self.assertEqual(code, 2 if invalid else 0)
                self.assertEqual(report["status"], "error" if invalid else "pass")
                self.assertIn("synthesis", report)
                self.assertIn("cited_documents", report)
                self.assertIn("prompt_hashes", report)
                self.assertEqual(report.get("judgment_validated", False), not invalid)
                self.assertEqual("Scores (0-4):" in stdout.getvalue(), not invalid)

    def test_http_does_not_retry_or_follow_redirects_or_include_error_bodies(self):
        seen = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                seen.append(self.path)
                self.send_response(307 if self.path == "/redirect" else 500)
                self.send_header("Location", "/followed")
                self.end_headers()
                self.wfile.write(b"secret provider error")
            def log_message(self, *args): pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            for path in ["/redirect", "/failed"]:
                with self.assertRaises(evaluation.EvaluationError) as error:
                    evaluation.http_json(f"http://127.0.0.1:{server.server_port}{path}", headers={"Authorization": "Bearer secret"})
                self.assertNotIn("secret", str(error.exception))
            self.assertEqual(seen, ["/redirect", "/failed"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
