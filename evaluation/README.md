# Evaluation

This folder contains the ground-truth question set, scoring rubrics, test runs, and analysis of retrieval and answer quality. Record successful answers as well as hallucinations, citation errors, contradictory-source behavior, stale-information behavior, and appropriate knowledge-gap detection.

Final reliability, cost-observation, and feasibility findings may also live here. Preserve enough inputs and methodology for results to be repeated and reviewed.

## One-shot synthesis evaluation

`scripts/evaluate_synthesis.py` is a manually invoked Python 3.10+ CLI using only the standard library. It is not imported by the UI, backend, normal frontend tests, or CI. Offline regression tests never contact a provider or the application.

Start the usual Spring/gbrain stack and ingest the canonical Markdown files from `synthetic-data/documents` first. Use a standard USER account, since completed knowledge is shared. The script does not upload, edit, delete, or reindex content, and does not clear caches. It reads the root `.env` as data, with shell variables taking precedence.

Set `OPENROUTER_API_KEY` for the two evaluator calls. For application authentication, set `KB_EVAL_TOKEN` or both `KB_EVAL_EMAIL` and `KB_EVAL_PASSWORD`, in the shell or untracked `.env`. The example file documents these options. Avoid putting tokens/passwords in command arguments. For example, in PowerShell:

```powershell
$env:KB_EVAL_EMAIL = "user@acme.example"
$env:KB_EVAL_PASSWORD = "<your local account password>"
python -B scripts/evaluate_synthesis.py --doc-id ACME-013
```

Explicit multiple sources, or reproducible document selection:

```powershell
python -B scripts/evaluate_synthesis.py --doc-id ACME-011 --doc-id ACME-012
python -B scripts/evaluate_synthesis.py --count 2 --seed 42
```

With no IDs, the script selects one `current` document by default. Selection uses a local seeded RNG; model outputs are not guaranteed to be deterministic. Up to three distinct documents can be selected. Explicit IDs may include archived/incomplete records, so review their authority and scope. The fixed 50-file corpus and evaluation answer keys are not modified.

### What one run does

1. Check selected files against the corpus catalog checksums. Find their completed application items by comparing full document content, ignoring only BOM, CRLF/LF and outer whitespace. AI-renamed titles do not affect identity. Duplicate imports with identical content are valid aliases. Missing, changed or inaccessible sources produce an inconclusive error before question generation.
2. Ask an AI to generate one business question, an expected answer, and essential criteria requiring every selected document. Each criterion must have a verifiable source quote. Only selected document text and metadata go to the generator; evaluation answer keys do not. The benchmark answer is kept out of the synthesis request.
3. Send exactly one `{question}` request to Spring's `/api/query/synthesize`, with no selected-source hint. Product retrieval and synthesis still go through Spring's gbrain adapter.
4. Check citation IDs deterministically: **every selected source must be cited**. A handbook or other equivalent source does not substitute for a selected document. Fetch every actual cited body through the authorized content endpoint; recheck selected bodies for changes during the run.
5. In a separate AI conversation, judge accuracy, grounding and completeness against the question, essential criteria, selected-source benchmark and actual cited bodies. The generated expected answer stays out of the judge input to reduce confusion with the candidate answer. Every supported criterion requires both a quote from the candidate answer and supporting evidence from an actual cited body; code verifies both quotes. Answer quotes are constrained in the JSON Schema to exact passages extracted from the candidate answer, plus an empty option for missing facts. A fact appearing only in a source cannot stand in for an omitted answer fact. A malformed, refused, truncated or unverifiable judge result cannot pass.
6. Save inputs, source mappings/hashes, query, answer, citations, document snapshots, criterion verdicts, scores, model names and reported evaluator usage to a JSON report. Authentication headers and credentials are excluded. Default reports go to ignored `evaluation/runs/`; keep custom `--output` paths outside version control. Existing reports are never overwritten.

### Pass/fail and limitations

A pass requires all selected sources in the references, an AI-synthesized answer rather than an extractive fallback, every essential criterion supported, no material unsupported claims or contradictions, and **at least 3/4 in each dimension**. Use `--min-score 4` for the strictest score gate. Thresholds and deterministic failures are enforced in code, not delegated to an AI's overall verdict.

Exit codes: **0** for pass (or an explicitly labeled dry run), **1** for a completed quality failure, **2** for a configuration, transport, input or evaluator error. An error is inconclusive, not evidence that the answer was good or bad. Reports preserve the stage reached and any response already received. Raw judge output remains available for diagnosis, but console scores appear only after judge validation succeeds (`judgment_validated: true`).

Two OpenRouter requests (generation and judging) plus one application synthesis request can incur provider charges and update application query usage/cache state. Requests are never retried automatically. A cached synthesis is allowed and recorded by default; `--require-fresh` fails a cache hit without purging the cache. Canceling the CLI does not guarantee that already-started provider/server work stops.

Question generation defaults to `anthropic/claude-haiku-4.5`; judging defaults to `openai/gpt-4.1` to use a different model family. Override `--generator-model` and `--judge-model` with compatible OpenRouter model IDs; structured JSON Schema output is required. Using the same model family for generation, application synthesis and judging can introduce correlated errors. A different judge model can reduce that dependence, but human review is still needed: this is a one-question smoke evaluation, not the 60-case reliability study or proof of factual correctness. Overlapping selected documents can make the strict all-source requirement harder than ordinary user Q&A.

Large contexts fail explicitly rather than silently dropping references. `--max-context-chars` bounds the generated judge input (default 120,000 characters); HTTP responses are bounded at 8 MiB and each request times out after 180 seconds. Remote application URLs require HTTPS; HTTP is accepted only for loopback. Redirects are rejected to avoid forwarding credentials.

### Local checks without paid calls

```powershell
python -B scripts/evaluate_synthesis.py --dry-run --doc-id ACME-013
python -B -m unittest discover -s scripts/tests -p "test_evaluate_synthesis.py" -v
python synthetic-data/tools/validate.py
```

`--dry-run` selects and validates local files only. It does not authenticate, check ingestion, call synthesis or use AI; its report is marked `dry_run`, not `pass`. `--api-url`, `--env-file`, and `--output` support other local deployments and report locations. Run `--help` for all options.

