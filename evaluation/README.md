# Evaluation

This folder contains the ground-truth question set, scoring rubrics, test runs, and analysis of retrieval and answer quality. Record successful answers as well as hallucinations, citation errors, contradictory-source behavior, stale-information behavior, and appropriate knowledge-gap detection.

Final reliability, cost-observation, and feasibility findings may also live here. Preserve enough inputs and methodology for results to be repeated and reviewed.

## Command-line synthesis evaluation

`scripts/evaluate_synthesis.py` is a manually invoked Python 3.10+ CLI using only the standard library. It is not imported by the UI, backend, normal frontend tests, or CI. Offline regression tests never contact a provider or the application.

Start the usual Spring/gbrain stack and ingest the canonical Markdown files from `synthetic-data/documents` first. Use a standard USER account, since completed knowledge is shared. The script does not upload, edit, delete, or reindex content, and does not clear caches. It reads the root `.env` as data, with shell variables taking precedence.

For evaluator calls, set `OPENROUTER_API_KEY` or `OPENAI_API_KEY`. A nonblank OpenRouter key takes precedence; when it is absent or blank, the CLI uses the OpenAI key directly. If neither is set, it fails before application/provider requests. For application authentication, set `KB_EVAL_TOKEN` or both `KB_EVAL_EMAIL` and `KB_EVAL_PASSWORD`, in the shell or untracked `.env`. The example file documents these options. Avoid putting tokens/passwords in command arguments. For example, in PowerShell:

```powershell
$env:KB_EVAL_EMAIL = "user@acme.example"
$env:KB_EVAL_PASSWORD = "<your local account password>"
python -B scripts/evaluate_synthesis.py --doc-id ACME-013
```

### Multiple documents and questions

Run several distinct questions **per document** in one invocation:

```powershell
python -B scripts/evaluate_synthesis.py --doc-id ACME-013 --doc-id ACME-017 --queries-per-doc 3
python -B scripts/evaluate_synthesis.py --count 5 --queries-per-doc 3 --seed 42
python -B scripts/evaluate_synthesis.py --count 5 --queries-per-doc 3 --seed 42 --dry-run
```

The first command plans six cases (three for each specified document); the second plans 15 cases. Each question is generated from just its target document, which must appear in that case's references. Other cited documents can also support the answer. Source matching runs once for the batch, then selected bodies are rechecked during each case. Execution is sequential.

With no IDs, the script selects one `current` document by default. `--count` samples available current documents without replacement, up to 50; a count larger than the available current pool fails clearly. Alternatively, repeat `--doc-id` for up to 50 distinct canonical documents. These selection options are mutually exclusive. `--queries-per-doc` accepts 1-10 and defaults to one, so the existing single-document command still attempts one question. Explicit IDs may include archived/incomplete records; review their authority and scope. The fixed corpus and evaluation answer keys are not modified.

Each later question receives earlier questions for that document and a rotating focus (facts/rules, practical application, limits, ownership/timing, comparisons). The generator is instructed to cover different facts or applications. Exact repeats, ignoring case and whitespace, become inconclusive errors before submission and are never retried. Semantic diversity still requires review. Document selection is reproducible with a local seed; model outputs are not guaranteed to be deterministic.

Previously, selecting multiple documents generated one question requiring all of them. Retain that behavior explicitly with `--combined` (1-3 documents and one question):

```powershell
python -B scripts/evaluate_synthesis.py --combined --doc-id ACME-011 --doc-id ACME-012
```

### What each case does

1. Check selected files against the corpus catalog checksums. Find their completed application items by comparing full document content, ignoring only BOM, CRLF/LF and outer whitespace. AI-renamed titles do not affect identity. Duplicate imports with identical content are valid aliases. Missing sources produce inconclusive errors for their cases before question generation; other documents can continue. Batch-wide configuration, authentication or source-list failures stop the run.
2. Ask an AI to generate one business question, an expected answer, and essential criteria requiring every selected document. Each criterion selects an ID from a catalog of source passages. Python resolves it to the exact source document and quote, rather than asking the AI to copy or reconstruct text. Only selected document text, passage catalogs and metadata go to the generator; evaluation answer keys do not. The benchmark answer is kept out of the synthesis request.
3. Send exactly one `{question}` request to Spring's `/api/query/synthesize`, with no selected-source hint. Product retrieval and synthesis still go through Spring's gbrain adapter.
4. Check citation IDs deterministically: **every selected source must be cited**. A handbook or other equivalent source does not substitute for a selected document. Fetch every actual cited body through the authorized content endpoint; recheck selected bodies for changes during the run.
5. In a separate AI conversation, judge accuracy, grounding and completeness against the question, essential criteria, selected-source benchmark and actual cited bodies. The generated expected answer stays out of the judge input to reduce confusion with the candidate answer. Every supported criterion selects an answer passage ID and actual cited evidence passage IDs. Python derives their text and document identities, then verifies them against the original bodies. Verdict-specific schema branches require actual answer/evidence IDs for supported or contradicted facts; missing facts use the answer ID `NONE`. Only short ASCII IDs enter schema enums; quotations, line breaks and Unicode document text remain in the prompt data, avoiding provider schema errors from full-text enum values. A fact appearing only in a source cannot stand in for an omitted answer fact. Unknown IDs, missing/duplicate criterion indices, malformed or truncated results remain inconclusive errors.
6. Save inputs, source mappings/hashes, query, answer, citations, document snapshots, criterion verdicts, scores, model names and reported evaluator usage to a JSON report. Authentication headers and credentials are excluded. Default reports go to ignored `evaluation/runs/`; keep custom `--output` paths outside version control. Existing reports are never overwritten.

### Reports and progress

The console prints the case/document/query number, result, validated scores, and reasons for missing or contradicted criteria. Individual quality failures or evaluator errors do not stop remaining cases. The final summary lists pass/fail/error/not-run counts per document and overall, cache-hit counts, and average scores over validated, completed judgments only. Pass rate uses **passed / planned**, so errors and unrun cases never count as passes. Average scores cannot override an individual case's failed criteria.

The generator must make criteria atomic and relevant to its question. If it nevertheless introduces an unrelated criterion (for example, office-attendance rules for a travel-claim question), the judge may mark it `not_applicable` with a rationale. Such criteria are displayed for review and excluded from required-criterion coverage and failure gates. A relevant fact omitted from the answer must be `missing`, not `not_applicable`. Every selected document must retain at least one applicable criterion; otherwise the generated rubric is inconclusive, preventing an empty or wholly irrelevant rubric from passing.

Reports preserve the model's original scores in `judgment.raw_scores`. Effective `judgment.scores` apply explicit consistency caps: completeness cannot exceed `floor(4 × supported applicable criteria / total applicable criteria)`; contradictions cap accuracy at 2; unsupported material claims cap grounding at 2. These checks only lower scores, and `score_adjustments` records each change. The console prints effective scores, required-criterion coverage, out-of-scope counts, and adjustments; summary averages use effective scores. An omitted required fact cannot receive a perfect effective completeness score even if the judge reports 4/4.

Report format version **2** stores each case under `cases[]` (including single-case runs). Read `cases[i].synthesis`, `.judgment`, `.citation_check`, `.error`, and `.evaluator_calls`; earlier reports stored those fields at the root. Root `summary`, `document_summaries`, `plan`, and `evaluator_calls` describe the whole batch. Selected-source snapshots, source mappings and model/prompt metadata remain at the root as well. `provider` and resolved `models` identify which evaluator route was actually selected; each evaluator call also records its provider. Dry runs do not select an evaluator provider. Per-case snapshots and usage make individual results reviewable independently.

Rubric `synthesis-batch-v4` retains selected passage IDs alongside Python-resolved `quote`, `answer_quote`, and evidence `item_id` fields. Full source/answer snapshots remain available. Passage catalogs use contiguous paragraph chunks of at most 3,500 characters with whitespace normalized; source evidence candidates require at least 20 characters. Heading-only/short blocks remain in the full document context. Schema catalogs are bounded below the provider's enum limits; oversized catalogs fail explicitly rather than silently discarding content.

The report is atomically checkpointed before and after each case. Local file replacement makes up to three attempts for temporary Windows reader locks; network requests are never retried. Ctrl+C stops further cases, saves any in-flight case as interrupted/inconclusive, and preserves earlier results and the unrun plan. A forced process termination may leave the active case marked `running`; earlier checkpoints remain available. There is no automatic resume or query retry.

### Pass/fail and limitations

A case passes only with all its selected sources in the references, an AI-synthesized answer rather than an extractive fallback, every essential criterion supported, no material unsupported claims or contradictions, and **at least 3/4 in each dimension**. Use `--min-score 4` for the strictest score gate. Thresholds and deterministic failures are enforced in code, not delegated to an AI's overall verdict. The batch passes only if every planned case passes.

Exit codes: **0** when every case passes (or an explicitly labeled dry run), **1** for completed runs with quality failures and no evaluator errors, **2** if any case has an evaluator error, the batch cannot start, or the run is interrupted. Errors take precedence over quality failures and remain inconclusive. Reports preserve the stage reached and any response already received. Raw judge output remains available for diagnosis, but console scores appear only after judge validation succeeds (`judgment_validated: true`).

Each completed case uses two AI requests to the selected evaluator provider (generation and judging) plus one application synthesis request. A 15-case batch can make 30 evaluator calls and 15 synthesis requests, incur provider charges, and update application query usage/cache state. Early errors can reduce those counts. The script prints the planned case count and selected evaluator route/models before calling AI; preview the plan with `--dry-run`. Requests are never retried automatically. A cached synthesis is allowed and recorded by default; `--require-fresh` fails a cache hit without purging the cache or forcing a fresh answer. Canceling the CLI does not guarantee that already-started provider/server work stops.

### Evaluator providers and models

| Selected provider | When used | Default generator | Default judge |
| --- | --- | --- | --- |
| OpenRouter | `OPENROUTER_API_KEY` is nonblank | `anthropic/claude-haiku-4.5` | `openai/gpt-4.1` |
| OpenAI directly | OpenRouter key absent/blank and `OPENAI_API_KEY` set | `gpt-4.1-mini` | `gpt-4.1` |

Both routes use Chat Completions with strict JSON Schema output ([OpenAI API reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)). OpenRouter-specific request fields are omitted from direct OpenAI requests. This selection applies to the CLI's question generator and judge; Spring/gbrain keeps its separately configured synthesis provider.

Override `--generator-model` and `--judge-model` with IDs compatible with the selected provider and request parameters. Direct OpenAI accepts native IDs such as `gpt-4.1-mini` or an `openai/` prefix, which is stripped; other provider-prefixed IDs are rejected before any request. OpenRouter retains its existing model-ID behavior. An invalid/rejected OpenRouter key or provider error does **not** trigger automatic failover or retries; clear the OpenRouter key in the shell or `.env` to select OpenAI instead.

Using the same model family for generation, application synthesis and judging can introduce correlated errors; the OpenAI fallback uses two GPT-4.1 variants. A different judge model can reduce that dependence, but human review is still needed: generated questions do not replace the fixed 60-case reliability study or prove factual correctness. Overlapping selected documents can make the strict selected-source requirement harder than ordinary user Q&A.

Large contexts fail explicitly rather than silently dropping references. `--max-context-chars` bounds the generated judge input (default 120,000 characters); HTTP responses are bounded at 8 MiB and each request times out after 180 seconds. Remote application URLs require HTTPS; HTTP is accepted only for loopback. Redirects are rejected to avoid forwarding credentials.

HTTP errors include recognized provider error types/codes/parameter names when available, without logging arbitrary response messages, credentials or request headers. They remain inconclusive evaluator errors, and requests are never retried automatically.

### Local checks without paid calls

```powershell
python -B scripts/evaluate_synthesis.py --dry-run --doc-id ACME-013
python -B -m unittest discover -s scripts/tests -p "test_evaluate_synthesis.py" -v
python synthetic-data/tools/validate.py
```

`--dry-run` selects and validates local files only. It does not authenticate, check ingestion, call synthesis or use AI; its report is marked `dry_run`, not `pass`. `--api-url`, `--env-file`, and `--output` support other local deployments and report locations. Run `--help` for all options.

