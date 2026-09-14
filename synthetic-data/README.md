# Synthetic Data

This folder contains **Acme v1**: 50 synthetic Markdown business documents for KnowledgeBridge, totaling approximately 10,000 body words, with recurring people, departments, projects, systems, policies, and cross-document references. The reference date is **September 11, 2026**. All people, organizations, customers, vendors, incidents, and financial amounts are fictional.

Keep the non-ingested dataset catalog and expected facts clearly separated from documents intended for ingestion. Do not place real employee, customer, credential, financial-account, or confidential business data here.

## Folder layout

```text
synthetic-data/
  documents/                 # Ingest only this directory: exactly 50 .md files
    01-company/              # 5 overviews, charters, service ownership, directories
    02-people/               # 5 employee profiles and a delivery-team RACI
    03-policies/             # 8 policies, procedures, approval matrix, archived policy
    04-projects/             # 7 charters, plans, status snapshots, closure report
    05-meetings/             # 8 steering, discovery, budget, kickoff, security reviews
    06-architecture/         # 5 accepted decisions and an unresolved proposal
    07-incidents/            # 4 incident reports and a recovery-drill retrospective
    08-products/             # 4 service guides, integration reference, SOW template
    09-training/             # 2 onboarding and analyst-training documents
    10-finance-vendors/      # 2 vendor assessment and project-budget register
  reference/                 # DO NOT INGEST: catalog, expected facts, answer keys
  tools/validate.py          # Standard-library structural and arithmetic checks
```

The distribution follows section 8 of the capstone execution plan. Matrices, budgets, checklists, runbooks, meeting minutes, and contract templates are represented as Markdown because that is the MVP input format. This corpus does not establish support for PDF, Word, Excel, email import, or image OCR.

## Company baseline

Acme is a 120-person software and consulting company with six departments and four service lines: **Atlas** workflow automation, **Beacon** operational analytics, **Harbor** managed integrations, and advisory services. Active projects are **Cedar**, **Aurora**, **Ledger**, and **Compass**; Access Cleanup is completed. Procurement is a team within Finance and Procurement, not a separate department. The company overview and directory provide the department counts and leadership.

Acme matches the organization name in the frontend scaffold, but this corpus is the controlled testing baseline. The frontend's hard-coded sample metrics and 2024 document labels are independent visual fixtures; they are not measurements of this dataset and have not been wired to these files.

## Document format

Each file has a stable `ACME-001`–`ACME-050` ID, descriptive filename, UTF-8 content, LF line endings, and YAML front matter. Values use JSON-compatible YAML scalars/lists, allowing the validation script to parse them without third-party dependencies. Fields include title, document type, organization, owner, created/updated dates, department, status, synthetic flag, related people/systems/projects, related document IDs, and supersession where applicable.

Dates are ISO dates; financial examples use USD. Created/updated dates describe the record, while events and effective dates appear in its body. Use the **effective date and source authority**, not just the most recent edit date, when interpreting policies. Related-document links are relative and remain valid if the entire `documents/` tree is preserved. The metadata is a dataset convention; the backend importer still needs to map it into application fields.

## Testing coverage

- Factual retrieval: people, roles, system names, incident durations, support targets.
- Synthesis: approval policy plus vendor quote, project gates plus incident evidence, customer onboarding plus scope.
- Tables and arithmetic: budget balances, cumulative snapshots, annual versus monthly commitments.
- Boundary conditions: exactly $5,000 and $20,000 versus amounts one cent above them; five attempts versus five retries.
- Temporal retrieval: current versus archived procurement policy and Cedar status snapshots.
- Conflicting sources: unapproved procurement notes versus the controlled matrix; Beacon's draft retention guide versus the standard.
- Knowledge gaps: salary, credentials, pricing, refunds, international mileage, regional hosting, and unrecorded cash payments.
- Ambiguity: unspecified project, retention record class, purchase date, or type of administrator access.

Draft and outdated records are deliberately included in `documents/`; do not silently discard them for the full reliability run. These are explicit, relatively easy authority-resolution fixtures, not covert adversarial prompt-injection tests. The remaining text is intended to be consistent. [AUTHORING.md](reference/AUTHORING.md) lists the exact exceptions and source hierarchy.

## Catalog and evaluation

Use [the catalog](reference/CATALOG.md) for a human-readable index and per-document expected facts. [catalog.json](reference/catalog.json) is the machine-readable manifest with paths, statuses, facts, and SHA-256 checksums.

[evaluation-questions.jsonl](reference/evaluation-questions.jsonl) contains 60 evaluation cases matching the plan's distribution: 15 direct lookups, 10 multi-document questions, 10 process/policy questions, 5 entity relationships, 8 gaps, 4 ambiguous questions, 4 conflicting-source questions, and 4 historical/superseded questions. Each records the expected answer, source IDs, acceptable wording, answer/gap/clarification behavior, and empty result/scoring fields. **Never ingest this file or the catalogs.**

The questions are authored fixtures, not evidence that a model has passed. Copy result records to your evaluation run directory before filling them in. For unsupported questions, listed sources document scope or the unresolved decision; they do not supply the missing answer. An adequate gap response should avoid guessing. Follow-up questions or semantically equivalent phrasing are acceptable where the record permits them.

## Validate locally

From the repository root, using Python 3.10 or newer:

```powershell
python synthetic-data/tools/validate.py
```

The validator checks counts, category distribution, IDs, required metadata, chronology, related links, hashes, question-source references, and budget arithmetic. It does not certify every semantic claim, privacy property, retrieval result, or ingestion path. Review the corpus and expected answers before treating it as the team's approved benchmark. No live ingestion or AI-provider call is performed by this command.

To revise a document, retain its ID, update its date where appropriate, review affected expected facts/questions, then update its catalog checksum. New revisions should preserve intentional historical evidence or explicitly document why an evaluation case changed. All authoring references remain outside `documents/`.
