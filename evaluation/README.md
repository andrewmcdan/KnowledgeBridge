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
2. Ask an AI to generate one focused business question, an expected answer, and essential criteria requiring every selected document. Each criterion selects an ID from a catalog of source passages. Python resolves it to the exact source document and quote. A separate validation conversation audits the entire benchmark against the supplied sources before synthesis; unsupported claims, misunderstood training questions, non-atomic criteria and questions requiring unsupplied linked bodies are rejected as inconclusive. Only selected document text, passage catalogs and metadata go to these calls; fixed evaluation answer keys do not. The benchmark answer is kept out of the synthesis request. AI validation reduces defects but does not establish human-reviewed truth.
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

Each completed generated case uses three evaluator AI requests (generation, benchmark validation and answer judging) plus one application synthesis request. A 15-case batch can make 45 evaluator calls and 15 synthesis requests, incur provider charges, and update application query usage/cache state. A fixed-suite case skips generation/benchmark validation and uses one answer-judging call plus one synthesis call. Early errors can reduce those counts. The script prints the planned case count and selected evaluator route/models before calling AI; preview with `--dry-run`. Requests are never retried automatically. A cached synthesis is allowed on the normal endpoint and recorded; `--require-fresh` fails a cache hit without clearing caches. The admin diagnostic endpoint always bypasses the answer cache. Canceling the CLI does not guarantee already-started work stops.

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

### Full-corpus PDF reporting

Use `python -B scripts/evaluate_synthetic_corpus.py --queries-per-doc 3` to run the existing evaluator against all 50 cataloged documents and generate both checkpointed JSON and a PDF report. This explicitly includes noncurrent documents that random `--count` selection excludes. The wrapper builds the PDF itself with ReportLab; install it using `python -m pip install -r scripts/requirements-evaluation.txt`.

The PDF includes the overview, per-document totals/averages, all planned query scores, judge reasoning, criterion verdicts and failure/error details. Scores are effective, validated scores; incomplete or erroneous cases stay unscored. The existing evaluator's pass-rate denominator, score consistency caps and AI-judging limitations remain in force. Full answers and evidence remain in the JSON audit file.

### Diagnostic rubric v5 and controlled comparisons

Strict pass/fail is retained for continuity. New reports also separate `answer_quality_status` (all quality gates except exact-target citation) from `citation_check.all_selected_cited`. Summary rates for strict passes, answer-quality passes and target-source citations all use the planned-case denominator; errors and unrun cases count as none of these. An accurate answer citing equivalent handbook evidence can pass answer quality while failing the exact-target test. No-citation answers fail grounding; empty evidence arrays and legacy missing-criterion `NONE` placeholders are handled without turning them into fabricated citations or judge errors. Old reports remain readable and are not retroactively rescored.

`diagnostic-suite.json` is a source-checked, fixed 20-question baseline with atomic criteria and exact canonical evidence quotes. It covers facts, procedures, boundaries, historical rules, conflicting authority and explicit knowledge gaps. It is separate from the original 60-case study and is not ingested. The correct Harbor contract is one initial attempt plus four retries. At runtime, suite dates, source checksums, IDs and evidence quotes are checked again. Questions/benchmarks stay fixed across comparison arms; only questions are submitted to synthesis.

```powershell
python -B scripts/evaluate_synthesis.py --suite evaluation/diagnostic-suite.json --dry-run
python -B scripts/evaluate_synthesis.py --suite evaluation/diagnostic-suite.json --case-id DIAG-13
```

For traced comparisons, explicitly enable `KNOWLEDGEBRIDGE_EVALUATION_ENABLED=true` and set the comma-separated `GBRAIN_EVALUATION_MODELS` allowlist in untracked `.env`. Rebuild/recreate gbrain and restart the backend so both receive the settings. Use an existing ADMIN evaluation account. Keep the normal synthesis model explicitly pinned with `GBRAIN_SYNTHESIS_MODEL`; correct `OPENROUTER_API_KEY` spelling before selecting providers. Neither correcting credentials nor comparison requests silently change the pinned baseline model.

```powershell
docker compose up -d --build gbrain
python -B scripts/compare_synthesis.py --model openai:gpt-4o-mini --model openrouter:anthropic/claude-haiku-4.5 --dry-run
python -B scripts/compare_synthesis.py --model openai:gpt-4o-mini --model openrouter:anthropic/claude-haiku-4.5
```

By default there are four arms (two models times corpus/workspace scopes), each with its own checkpointed JSON and PDF, plus frozen `suite.json` and `comparison.json`. Twenty cases per arm mean up to 80 synthesis calls and 80 judge calls. Use repeated `--case-id` or `--scope corpus` for a smaller diagnostic. Output directories must be new and default to ignored `evaluation/runs/comparison-<run-id>`.

`/api/query/evaluate` is opt-in, ADMIN-only and uncached. It still calls gbrain exclusively through the backend adapter. The pinned engine build applies the checked `docker/gbrain/patch-evaluation.mjs` instrumentation; upgrades fail the build if source anchors drift. Exact system/user prompts, pre-filter native candidates, selected chunks, scope and context SHA-256 are recorded for that actual call. Takes, graph and trajectory additions are disabled consistently in these diagnostic arms so their evidence is fully inspectable. Normal `/synthesize` keeps its ordinary pipeline and cache.

Corpus-only means an allowlist filter **after native gather, before prompt assembly**, using every unchanged canonical imported item (including matching aliases). Workspace means application pages gathered from the whole credential-scoped workspace, including the handbook. These are reversible per-request context scopes; nothing is removed/deactivated. The test isolates context composition, not a physically separate index or a recall benchmark over an isolated corpus. Compare `candidates` with `selected_pages` before attributing a result to ranking. Prompt strings show exactly what reached the model; full-document citations alone do not establish this.

Before diagnosing ranking, check embedding alignment:

```powershell
docker compose exec -T gbrain bun run src/cli.ts migrate embeddings --status --json
```

The environment, file and database planes must agree on the **provider-qualified** embedding model and dimensions, with zero stale/missing chunks. Direct OpenAI and OpenRouter routes have different generation identifiers even when they serve the same model at 1536 dimensions. A mismatch can exclude nearly the entire corpus from vector candidates. Pin `GBRAIN_EMBEDDING_MODEL` rather than relying on provider-key defaults for an existing index. Back up the gbrain database/configuration, align the environment, then use upstream's `migrate embeddings --to <provider:model> --dim 1536 --reranker keep --dry-run --json` to inspect work and cost. Apply with `--yes --max-cost-usd <reviewed-cap>` only after reviewing that plan. Do not rewrite generation identifiers manually. Recheck every configuration plane, stale/missing counts and auxiliary vector widths afterward.

The November 7 diagnostic repair aligned the existing OpenRouter generation, re-embedded 12 mismatched chunks, and repaired the takes vector width using that supported workflow. All 155 indexed pages then shared the same generation, with zero stale/missing chunks and 1536-dimensional content/cache/fact/take vectors. Pre-repair and post-repair comparison reports are separate ignored artifacts; the small diagnostic subset does not replace a full-corpus rerun.

Model score deltas are calculated only for cases with validated judgments and identical context hashes within the same scope. Context mismatches are reported as inconclusive model comparisons. Corpus/workspace arms intentionally have different context; they are not labeled matched model comparisons. Requests never override the server allowlist, retry synthesis, ingest answer keys, or transmit credentials to evaluator models. Traces can contain workspace content; keep them in ignored run directories.

For a known blank-line-only corpus/import mismatch, preview and optionally add a canonical Markdown copy:

```powershell
python -B scripts/reconcile_synthetic_imports.py --doc-id ACME-011
python -B scripts/reconcile_synthetic_imports.py --doc-id ACME-011 --apply
```

Repair requires an ADMIN account and a formatting-only matching import, preserves existing documents, refuses substantive changes, and skips sources already matched. It never deletes, rewrites, promotes users or retries uploads. Wait for ingestion completion and rerun the preview before evaluating. An interrupted upload may already have queued work; inspect the knowledge list before retrying.

Use `--dry-run` for an offline plan/PDF, or `--from-json <saved-report.json>` to generate a PDF without making or repeating requests. Default new-run artifacts are ignored `evaluation/runs/corpus-<run-id>/results.json` and `report.pdf`. See [script usage](../scripts/README.md#entire-corpus-with-a-pdf-report) for examples, options, dependency installation, recovery and exit codes.

