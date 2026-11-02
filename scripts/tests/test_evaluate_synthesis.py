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


def wire_generation(value, inputs):
    result = evaluation.deepcopy(value)
    for criterion in result["criteria"]:
        criterion["source_passage_id"] = next((p["passage_id"] for p in inputs["source_passages"]
            if p["doc_id"] == criterion["doc_id"] and p["text"] == criterion["quote"]), "INVALID")
        del criterion["doc_id"]
        del criterion["quote"]
    return result


def wire_judgment(value, inputs):
    result = evaluation.deepcopy(value)
    for criterion in result["criteria"]:
        quote = criterion.pop("answer_quote")
        criterion["answer_passage_id"] = "NONE" if quote == "" or criterion["verdict"] in {"missing", "not_applicable"} else next((p["passage_id"]
            for p in inputs["candidate_answer_passages"] if p["text"] == quote), "INVALID")
        criterion["evidence_passage_ids"] = [next((p["passage_id"] for p in inputs["cited_passages"]
            if p["item_id"] == item["item_id"] and p["text"] == item["quote"]), "INVALID") for item in criterion.pop("evidence")]
    return result


class FakeApp:
    def __init__(self):
        self.base = "http://127.0.0.1:8080"
        self.bodies = {10: BODY_ONE, 11: BODY_ONE, 20: BODY_TWO}
        self.synthesis_calls = []
        self.list_calls = 0
        self.response = {"answer": ANSWER,
                         "aiGenerated": True, "extractiveFallback": False, "cacheHit": False,
                         "citations": [{"itemKey": str(i), "externalId": f"knowledgebridge/{i}"} for i in [10, 20]]}
        self.changed = False

    def request(self, path, payload=None):
        if path == "/api/knowledge-items":
            self.list_calls += 1
            return [{"id": i, "status": "COMPLETED"} for i in self.bodies]
        assert path == "/api/query/synthesize", path
        self.synthesis_calls.append(payload)
        return self.response

    def content(self, item_id, fresh=False):
        return {"id": item_id, "title": "Renamed upload", "text": "A changed body" if fresh and self.changed else self.bodies[item_id]}


class FakeAI:
    def __init__(self):
        self.provider = "openrouter"
        self.models = {"generation": "generator", "judging": "judge"}
        self.generated = generated()
        self.judgment = judgment()
        self.requests = []
        self.schemas = []
        self.calls = []

    def ask(self, stage, instruction, inputs, schema):
        self.requests.append((stage, inputs))
        self.schemas.append(schema)
        return wire_generation(self.generated, inputs) if stage == "generation" else wire_judgment(self.judgment, inputs)


class BatchAI:
    def __init__(self, actions=None, repeat=False):
        self.provider = "openrouter"
        self.models = {"generation": "generator", "judging": "judge"}
        self.actions = actions or {}
        self.repeat = repeat
        self.requests = []
        self.calls = []
        self.number = 0

    def ask(self, stage, instruction, inputs, schema):
        self.requests.append((stage, evaluation.deepcopy(inputs)))
        self.calls.append({"stage": stage, "usage": {"total_tokens": 10}})
        if stage == "generation":
            self.number += 1
            self.selected = inputs["selected_documents"]
            value = generated(self.selected)
            subject = "production access" if self.selected[0]["doc_id"] == "ACME-013" else "expense claims"
            number = 1 if self.repeat else inputs.get("query_context", {}).get("query_number", 1)
            value["question"] = f"How should {subject} be handled in situation {number}?"
            return wire_generation(value, inputs)
        if self.actions.get(self.number) == "interrupt":
            raise KeyboardInterrupt
        value = judgment(self.selected)
        for item, doc in zip(value["criteria"], self.selected):
            item["evidence"][0]["item_id"] = 10 if doc["doc_id"] == "ACME-013" else 20
        if self.actions.get(self.number) == "fail":
            value["criteria"][0].update({"verdict": "missing", "answer_quote": "", "evidence": []})
        if self.actions.get(self.number) == "error":
            value["criteria"][0]["answer_quote"] = "An invented answer passage that does not exist."
        if self.actions.get(self.number) == "low_score":
            value["scores"]["accuracy"] = 2
        return wire_judgment(value, inputs)


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
        references = ai.schemas[1]["$defs"]["answer_passage"]["enum"]
        self.assertEqual(references, [p["passage_id"] for p in ai.requests[1][1]["candidate_answer_passages"]])
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

    def test_judge_schema_contains_only_short_ids_even_with_quoted_multiline_unicode_answers(self):
        answer = '# Access rules\n\nUse "SSO" and MFA [knowledgebridge/10].\nKeep the café approval ticket.\n\n' + "x" * 8001
        passages = evaluation.passage_catalog(answer, "A")
        sources = evaluation.cited_catalog([{"id": 10, "text": BODY_ONE}])
        schema = evaluation.judge_schema(passages, sources)
        choices = schema["$defs"]["answer_passage"]["enum"]
        self.assertEqual(choices, [p["passage_id"] for p in passages])
        self.assertTrue(all(evaluation.quote_in(p["text"], answer) for p in passages))
        self.assertTrue(all(choice.isascii() and '"' not in choice and "\n" not in choice for choice in choices))
        self.assertTrue(all(0 < len(p["text"]) <= 3500 for p in passages))
        self.assertEqual("".join(p["text"] for p in passages[-3:]), "x" * 8001)
        self.assertNotIn("enum", evaluation.JUDGE_SCHEMA["properties"]["criteria"]["items"]["properties"]["answer_passage_id"])

    def test_reported_fabricated_credential_quote_is_inconclusive_even_with_passing_scores(self):
        ai = FakeAI()
        ai.judgment["scores"] = {name: 3 for name in ["accuracy", "grounding", "completeness"]}
        ai.judgment["criteria"][0]["answer_quote"] = "Never copy production credentials or recovery codes into tickets, documents, or knowledge-base uploads."
        with self.assertRaisesRegex(evaluation.EvaluationError, "criterion 1.*unknown passage.*inconclusive"):
            self.run_case(ai=ai)

    def test_provider_cannot_ignore_the_answer_passage_enum(self):
        ai = FakeAI()
        ai.judgment["criteria"][0]["answer_quote"] = "Use company SSO and MFA."
        with self.assertRaisesRegex(evaluation.EvaluationError, "unknown passage"):
            self.run_case(ai=ai)

    def test_generation_resolves_exact_source_identity_and_text_instead_of_copied_quotes(self):
        passages = evaluation.source_catalog(SELECTED)
        raw = wire_generation(generated(), {"source_passages": passages})
        raw["criteria"][0].update({"doc_id": "ACME-999", "quote": "Fabricated text"})
        resolved = evaluation.resolve_generation(raw, passages)
        evaluation.validate_generation(resolved, SELECTED)
        self.assertEqual(resolved["criteria"][0]["doc_id"], "ACME-013")
        self.assertEqual(resolved["criteria"][0]["quote"], BODY_ONE)
        raw["criteria"][0]["source_passage_id"] = "UNKNOWN"
        with self.assertRaisesRegex(evaluation.EvaluationError, "unknown passage"):
            evaluation.resolve_generation(raw, passages)

    def test_judge_evidence_identity_comes_from_selected_cited_passage(self):
        answers = evaluation.passage_catalog(ANSWER, "A")
        documents = [FakeApp().content(i) for i in [10, 20]]
        sources = evaluation.cited_catalog(documents)
        raw = wire_judgment(judgment(), {"candidate_answer_passages": answers, "cited_passages": sources})
        raw["criteria"][0]["evidence"] = [{"item_id": 999, "quote": "Fabricated source"}]
        resolved = evaluation.resolve_judgment(raw, answers, sources)
        evaluation.validate_judgment(resolved, generated(), documents, ANSWER)
        self.assertEqual(resolved["criteria"][0]["evidence"][0]["item_id"], 10)
        self.assertEqual(resolved["criteria"][0]["evidence"][0]["quote"], BODY_ONE)
        raw["criteria"][0]["evidence_passage_ids"] = ["SACME-013-P0001"]
        with self.assertRaisesRegex(evaluation.EvaluationError, "unknown passage"):
            evaluation.resolve_judgment(raw, answers, sources)

    def test_consistency_scores_preserve_raw_judge_values_and_cap_missing_coverage(self):
        value = judgment()
        value["criteria"][1]["verdict"] = "missing"
        evaluation.apply_score_consistency(value)
        self.assertEqual(value["raw_scores"], {"accuracy": 4, "grounding": 4, "completeness": 4})
        self.assertEqual(value["scores"], {"accuracy": 4, "grounding": 4, "completeness": 2})
        self.assertEqual(len(value["score_adjustments"]), 1)
        value = judgment()
        value["criteria"][0]["verdict"] = "contradicted"
        value["unsupported_claims"] = ["Unsupported approval requirement"]
        evaluation.apply_score_consistency(value)
        self.assertEqual(value["scores"], {"accuracy": 2, "grounding": 2, "completeness": 2})
        value = judgment()
        value["scores"]["completeness"] = 1
        evaluation.apply_score_consistency(value)
        self.assertEqual(value["scores"]["completeness"], 1)
        self.assertEqual(value["score_adjustments"], [])

    def test_passage_schema_bounds_and_explicit_one_based_criterion_indices(self):
        answers = evaluation.passage_catalog(ANSWER, "A")
        sources = evaluation.cited_catalog([FakeApp().content(10)])
        schema = evaluation.judge_schema(answers, sources, 2)
        self.assertEqual(schema["$defs"]["criterion_index"]["enum"], [1, 2])
        large = [{"passage_id": f"A-P{i:04d}", "text": "A source paragraph."} for i in range(901)]
        with self.assertRaisesRegex(evaluation.EvaluationError, "Too many"):
            evaluation.judge_schema(large, sources)
        with self.assertRaisesRegex(evaluation.EvaluationError, "Too many"):
            evaluation.generation_schema(large)

    def test_supported_schema_requires_real_answer_and_cited_evidence(self):
        answers = evaluation.passage_catalog(ANSWER, "A")
        sources = evaluation.cited_catalog([FakeApp().content(10)])
        schema = evaluation.judge_schema(answers, sources, 1)
        branches = {item["properties"]["verdict"]["enum"][0]: item["properties"]
                    for item in schema["properties"]["criteria"]["items"]["anyOf"]}
        self.assertEqual(branches["supported"]["answer_passage_id"], {"$ref": "#/$defs/answer_passage"})
        self.assertNotIn("NONE", schema["$defs"]["answer_passage"]["enum"])
        self.assertEqual(branches["supported"]["evidence_passage_ids"]["minItems"], 1)
        self.assertEqual(branches["missing"]["answer_passage_id"]["enum"], ["NONE"])
        self.assertEqual(branches["not_applicable"]["evidence_passage_ids"]["maxItems"], 0)

    def test_irrelevant_criteria_do_not_penalize_answers_but_cannot_exclude_all_sources(self):
        benchmark = generated(SELECTED[:1])
        benchmark["criteria"].append(dict(benchmark["criteria"][0]))
        value = judgment(SELECTED[:1])
        value["criteria"].append({"index": 2, "verdict": "not_applicable", "rationale": "Office attendance is outside this access question.",
                                  "answer_quote": "", "evidence": []})
        cited = [FakeApp().content(10)]
        evaluation.validate_judgment(value, benchmark, cited, ANSWER)
        evaluation.apply_score_consistency(value)
        self.assertEqual(value["scores"]["completeness"], 4)
        value["criteria"][0].update({"verdict": "not_applicable", "answer_quote": "", "evidence": []})
        with self.assertRaisesRegex(evaluation.EvaluationError, "no applicable criteria"):
            evaluation.validate_judgment(value, benchmark, cited, ANSWER)
        value = judgment()
        value["criteria"][1].update({"verdict": "not_applicable", "answer_quote": "", "evidence": []})
        with self.assertRaisesRegex(evaluation.EvaluationError, "no applicable criteria"):
            evaluation.validate_judgment(value, generated(), [FakeApp().content(i) for i in [10, 20]], ANSWER)

    def test_citation_identifiers_are_consistent_and_positive(self):
        self.assertEqual(evaluation.citation_id({"itemKey": "010", "externalId": "knowledgebridge/10"}), 10)
        for citation in [{}, {"itemKey": "0"}, {"itemKey": "-1"}, {"itemKey": "10", "externalId": "knowledgebridge/20"}, {"itemKey": "10", "externalId": 10}]:
            with self.subTest(citation=citation), self.assertRaises(evaluation.EvaluationError): evaluation.citation_id(citation)

    def test_context_limits_fail_instead_of_silently_truncating(self):
        app, ai = FakeApp(), FakeAI()
        with self.assertRaisesRegex(evaluation.EvaluationError, "context"):
            limit = len(json.dumps({"as_of": "2026-09-11", "selected_documents": SELECTED,
                                   "source_passages": evaluation.source_catalog(SELECTED)})) + 1
            self.run_case(app, ai, max_context=limit)
        self.assertEqual([stage for stage, _ in ai.requests], ["generation"])
        app, ai = FakeApp(), FakeAI()
        with self.assertRaisesRegex(evaluation.EvaluationError, "context"):
            self.run_case(app, ai, max_context=10)
        self.assertEqual(ai.requests, [])

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

    def test_openrouter_is_preferred_when_both_keys_are_set(self):
        requests = []
        def transport(url, method, payload, headers):
            requests.append((url, payload, headers))
            return {"choices": [{"finish_reason": "stop", "message": {"content": "{}"}}]}
        ai = evaluation.EvaluatorAI({"OPENROUTER_API_KEY": "router-secret", "OPENAI_API_KEY": "openai-secret"}, transport=transport)
        ai.ask("generation", "Generate", {}, evaluation.GENERATION_SCHEMA)
        self.assertEqual(ai.provider, "openrouter")
        self.assertEqual(ai.models, {"generation": "anthropic/claude-haiku-4.5", "judging": "openai/gpt-4.1"})
        self.assertEqual(requests[0][0], "https://openrouter.ai/api/v1/chat/completions")
        self.assertEqual(requests[0][2], {"Authorization": "Bearer router-secret"})
        self.assertEqual(requests[0][1]["provider"], {"require_parameters": True})
        self.assertIn("max_tokens", requests[0][1])
        self.assertNotIn("secret", json.dumps(ai.calls))

    def test_absent_or_blank_router_key_uses_direct_openai_for_both_stages(self):
        for router in [None, "", " \t\n"]:
            requests = []
            def transport(url, method, payload, headers):
                requests.append((url, payload, headers))
                return {"choices": [{"finish_reason": "stop", "message": {"content": "{}"}}], "usage": {"total_tokens": 25}}
            env = {"OPENAI_API_KEY": "openai-secret"}
            if router is not None:
                env["OPENROUTER_API_KEY"] = router
            ai = evaluation.EvaluatorAI(env, transport=transport)
            ai.ask("generation", "Generate", {}, evaluation.GENERATION_SCHEMA)
            ai.ask("judging", "Judge", {}, evaluation.JUDGE_SCHEMA)
            with self.subTest(router=router):
                self.assertEqual(ai.provider, "openai")
                self.assertEqual([request[1]["model"] for request in requests], ["gpt-4.1-mini", "gpt-4.1"])
                for url, payload, headers in requests:
                    self.assertEqual(url, "https://api.openai.com/v1/chat/completions")
                    self.assertEqual(headers, {"Authorization": "Bearer openai-secret"})
                    self.assertEqual(payload["max_completion_tokens"], 3000)
                    self.assertEqual(payload["response_format"]["type"], "json_schema")
                    self.assertNotIn("provider", payload)
                    self.assertNotIn("max_tokens", payload)
                self.assertTrue(all(call["provider"] == "openai" for call in ai.calls))
                self.assertNotIn("secret", json.dumps(ai.calls))

    def test_openai_accepts_native_and_openai_prefixed_overrides_and_rejects_other_providers(self):
        env = {"OPENAI_API_KEY": "secret"}
        ai = evaluation.EvaluatorAI(env, "openai/gpt-4.1-mini", "gpt-4.1")
        self.assertEqual(ai.models, {"generation": "gpt-4.1-mini", "judging": "gpt-4.1"})
        for model in ["anthropic/claude-haiku-4.5", "openai/", " "]:
            with self.subTest(model=model), self.assertRaises(evaluation.EvaluationError):
                evaluation.EvaluatorAI(env, model, None, transport=lambda *args: self.fail("No request expected"))

    def test_missing_both_provider_keys_fails_before_transport(self):
        for env in [{}, {"OPENROUTER_API_KEY": " ", "OPENAI_API_KEY": "\t"}]:
            with self.subTest(env=env), self.assertRaisesRegex(evaluation.EvaluationError, "OPENROUTER_API_KEY or OPENAI_API_KEY"):
                evaluation.EvaluatorAI(env, transport=lambda *args: self.fail("No request expected"))

    def test_a_rejected_router_request_does_not_silently_switch_provider(self):
        requests = []
        def transport(url, *args):
            requests.append(url)
            raise evaluation.EvaluationError("HTTP 401; no request was retried.")
        ai = evaluation.EvaluatorAI({"OPENROUTER_API_KEY": "router", "OPENAI_API_KEY": "openai"}, transport=transport)
        with self.assertRaisesRegex(evaluation.EvaluationError, "401"):
            ai.ask("generation", "Generate", {}, evaluation.GENERATION_SCHEMA)
        self.assertEqual(requests, ["https://openrouter.ai/api/v1/chat/completions"])

    def test_cli_records_resolved_openai_provider_and_models_without_credentials(self):
        def transport(url, method, payload, headers):
            inputs = json.loads(payload["messages"][1]["content"])
            value = wire_generation(generated(SELECTED[:1]), inputs) if payload["model"] == "gpt-4.1-mini" else wire_judgment(judgment(SELECTED[:1]), inputs)
            return {"choices": [{"finish_reason": "stop", "message": {"content": json.dumps(value)}}]}
        ai = evaluation.EvaluatorAI({"OPENAI_API_KEY": "private-openai-key"}, transport=transport)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.json"
            with patch.object(evaluation, "load_documents", return_value=("2026-09-11", SELECTED[:1])), patch.object(evaluation, "Application", return_value=FakeApp()), patch.object(evaluation, "EvaluatorAI", return_value=ai), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                code = evaluation.main(["--doc-id", "ACME-013", "--output", str(path)])
            report = json.loads(path.read_text())
            self.assertEqual(code, 0)
            self.assertEqual(report["provider"], "openai")
            self.assertEqual(report["models"], {"generator": "gpt-4.1-mini", "judge": "gpt-4.1"})
            self.assertTrue(all(call["provider"] == "openai" for call in report["evaluator_calls"]))
            self.assertNotIn("private-openai-key", json.dumps(report))

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
                    code = evaluation.main(["--doc-id", "ACME-013", "--combined", "--output", str(path)])
                report = json.loads(path.read_text())
                self.assertEqual(code, 2 if invalid else 0)
                self.assertEqual(report["status"], "error" if invalid else "pass")
                self.assertEqual(report["report_format_version"], 2)
                self.assertEqual(len(report["cases"]), 1)
                self.assertIn("synthesis", report["cases"][0])
                self.assertIn("cited_documents", report["cases"][0])
                self.assertIn("prompt_hashes", report)
                self.assertEqual(report["cases"][0].get("judgment_validated", False), not invalid)
                self.assertEqual("Scores (0-4):" in stdout.getvalue(), not invalid)

    def test_batch_runs_distinct_queries_per_document_and_maps_sources_once(self):
        app, ai, report, checkpoints = FakeApp(), BatchAI(), {}, []
        with redirect_stdout(io.StringIO()):
            code = evaluation.run_batch(app, ai, SELECTED, "2026-09-11", report, queries_per_doc=3,
                                         checkpoint=lambda: checkpoints.append(evaluation.deepcopy(report)))
        self.assertEqual(code, 0)
        self.assertEqual(app.list_calls, 1)
        self.assertEqual(len(app.synthesis_calls), 6)
        self.assertEqual(len({call["question"] for call in app.synthesis_calls}), 6)
        self.assertTrue(all(set(call) == {"question"} for call in app.synthesis_calls))
        self.assertEqual(report["summary"]["passed"], 6)
        self.assertEqual(report["summary"]["not_run"], 0)
        self.assertEqual(report["summary"]["average_scores"]["accuracy"], 4)
        self.assertEqual(report["document_summaries"]["ACME-017"]["passed"], 3)
        self.assertEqual(len(checkpoints), 12)
        self.assertEqual(checkpoints[0]["cases"][0]["status"], "running")
        generations = [inputs for stage, inputs in ai.requests if stage == "generation"]
        self.assertEqual([len(inputs["query_context"]["previous_questions"]) for inputs in generations], [0, 1, 2, 0, 1, 2])
        self.assertTrue(all(len(inputs["selected_documents"]) == 1 for inputs in generations))
        self.assertEqual([case["source_item_ids"] for case in report["cases"]],
                         [{"ACME-013": [10, 11]}] * 3 + [{"ACME-017": [20]}] * 3)
        self.assertTrue(all(len(case["evaluator_calls"]) == 2 for case in report["cases"]))

    def test_batch_continues_after_quality_failures_and_invalid_judgments(self):
        report = {}
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = evaluation.run_batch(FakeApp(), BatchAI({1: "fail", 2: "error", 3: "low_score"}), SELECTED, "2026-09-11", report, queries_per_doc=2)
        self.assertEqual(code, 2)
        self.assertEqual([case["status"] for case in report["cases"]], ["fail", "error", "fail", "pass"])
        self.assertEqual(report["summary"]["passed"], 1)
        self.assertEqual(report["summary"]["failed"], 2)
        self.assertEqual(report["summary"]["errors"], 1)
        self.assertEqual(report["summary"]["scored_cases"], 3)
        self.assertEqual(report["summary"]["pass_rate"], 0.25)
        self.assertEqual(report["summary"]["average_scores"]["accuracy"], 3.333)

    def test_batch_quality_failure_without_evaluator_errors_exits_one(self):
        report = {}
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = evaluation.run_batch(FakeApp(), BatchAI({1: "fail"}), SELECTED, "2026-09-11", report)
        self.assertEqual(code, 1)
        self.assertEqual(report["status"], "fail")
        self.assertEqual(report["summary"]["completed"], 2)

    def test_duplicate_questions_are_not_submitted_or_retried(self):
        app, ai, report = FakeApp(), BatchAI(repeat=True), {}
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = evaluation.run_batch(app, ai, SELECTED[:1], "2026-09-11", report, queries_per_doc=2)
        self.assertEqual(code, 2)
        self.assertEqual(len(app.synthesis_calls), 1)
        self.assertEqual([stage for stage, _ in ai.requests], ["generation", "judging", "generation"])
        self.assertIn("repeated an earlier question", report["cases"][1]["error"])

    def test_missing_document_does_not_prevent_other_documents_from_running(self):
        app, ai, report = FakeApp(), BatchAI(), {}
        del app.bodies[10]
        del app.bodies[11]
        app.response["citations"] = [{"itemKey": "20", "externalId": "knowledgebridge/20"}]
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = evaluation.run_batch(app, ai, SELECTED, "2026-09-11", report, queries_per_doc=2)
        self.assertEqual(code, 2)
        self.assertEqual([case["status"] for case in report["cases"]], ["error", "error", "pass", "pass"])
        self.assertEqual(len(app.synthesis_calls), 2)
        self.assertEqual(len(ai.calls), 4)

    def test_interrupt_preserves_completed_and_interrupted_cases_and_remaining_plan(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.json"
            app, ai = FakeApp(), BatchAI({2: "interrupt"})
            with patch.object(evaluation, "load_documents", return_value=("2026-09-11", SELECTED)), patch.object(evaluation, "Application", return_value=app), patch.object(evaluation, "EvaluatorAI", return_value=ai), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                code = evaluation.main(["--count", "2", "--queries-per-doc", "2", "--output", str(path)])
            report = json.loads(path.read_text())
            self.assertEqual(code, 2)
            self.assertEqual(report["status"], "error")
            self.assertEqual([case["status"] for case in report["cases"]], ["pass", "error"])
            self.assertEqual(report["summary"]["completed"], 2)
            self.assertEqual(report["summary"]["not_run"], 2)
            self.assertEqual(len(app.synthesis_calls), 2)
            self.assertEqual(report["document_summaries"]["ACME-017"]["not_run"], 2)

    def test_dry_run_plans_multiple_documents_and_queries_without_network(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.json"
            with patch.object(evaluation, "Application", side_effect=AssertionError("No network")), patch.object(evaluation, "EvaluatorAI", side_effect=AssertionError("No paid calls")), redirect_stdout(io.StringIO()):
                code = evaluation.main(["--count", "5", "--queries-per-doc", "3", "--dry-run", "--output", str(path)])
            report = json.loads(path.read_text())
            self.assertEqual(code, 0)
            self.assertEqual(report["status"], "dry_run")
            self.assertEqual(report["summary"]["planned"], 15)
            self.assertEqual(report["summary"]["not_run"], 15)
            self.assertEqual(report["cases"], [])

    def test_combined_constraints_and_selection_bounds_fail_before_network(self):
        for args in [["--combined", "--count", "4"], ["--combined", "--queries-per-doc", "2"]]:
            with self.subTest(args=args), tempfile.TemporaryDirectory() as folder:
                with patch.object(evaluation, "Application", side_effect=AssertionError("No network")), patch.object(evaluation, "EvaluatorAI", side_effect=AssertionError("No paid calls")), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                    self.assertEqual(evaluation.main([*args, "--output", str(Path(folder) / "run.json")]), 2)
        _, docs = evaluation.load_documents(["ACME-011", "ACME-012", "ACME-013", "ACME-017"], 1, 42)
        self.assertEqual(len(docs), 4)
        with self.assertRaisesRegex(evaluation.EvaluationError, "available current"):
            evaluation.load_documents(None, 51, 42)

    def test_failed_checkpoint_replacement_preserves_previous_report_and_cleans_temporary(self):
        for error_type in [OSError, PermissionError]:
            with self.subTest(error_type=error_type), tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "run.json"
                path.write_text('{"checkpoint": 1}\n', encoding="utf-8")
                with patch.object(evaluation.os, "replace", side_effect=error_type("write failure")) as replace, patch.object(evaluation.time, "sleep"), self.assertRaises(OSError):
                    evaluation.write_report(path, {"checkpoint": 2})
                self.assertEqual(replace.call_count, 3 if error_type is PermissionError else 1)
                self.assertEqual(json.loads(path.read_text()), {"checkpoint": 1})
                self.assertEqual(list(Path(folder).iterdir()), [path])

    def test_transient_windows_checkpoint_lock_retries_only_local_replacement(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.json"
            path.write_text('{"checkpoint": 1}\n', encoding="utf-8")
            original = evaluation.os.replace
            attempts = []
            def replace(source, destination):
                attempts.append(destination)
                if len(attempts) < 3:
                    raise PermissionError("Temporary reader lock")
                original(source, destination)
            with patch.object(evaluation.os, "replace", side_effect=replace), patch.object(evaluation.time, "sleep") as sleep:
                evaluation.write_report(path, {"checkpoint": 2})
            self.assertEqual(len(attempts), 3)
            self.assertEqual(sleep.call_count, 2)
            self.assertEqual(json.loads(path.read_text()), {"checkpoint": 2})
            self.assertEqual(list(Path(folder).iterdir()), [path])

    def test_http_does_not_retry_or_follow_redirects_or_include_error_bodies(self):
        seen = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                seen.append(self.path)
                self.send_response(307 if self.path == "/redirect" else 400 if self.path == "/schema" else 500)
                self.send_header("Location", "/followed")
                self.end_headers()
                body = json.dumps({"error": {"type": "invalid_request_error", "code": "invalid_json_schema",
                                             "param": "response_format", "message": "secret provider error"}}).encode()
                self.wfile.write(body if self.path == "/schema" else b"secret provider error")
            def log_message(self, *args): pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            for path in ["/redirect", "/failed", "/schema"]:
                with self.assertRaises(evaluation.EvaluationError) as error:
                    evaluation.http_json(f"http://127.0.0.1:{server.server_port}{path}", headers={"Authorization": "Bearer secret"})
                self.assertNotIn("secret", str(error.exception))
                if path == "/schema":
                    self.assertIn("code=invalid_json_schema", str(error.exception))
                    self.assertIn("param=response_format", str(error.exception))
            self.assertEqual(seen, ["/redirect", "/failed", "/schema"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
