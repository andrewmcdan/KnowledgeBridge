# Acme .docx fixtures

Three synthetic Acme Word documents for testing `.docx` upload and ingestion. They are separate from `documents/`, which stays the 50-file Markdown corpus checked by `tools/validate.py`, and they are not part of the evaluation questions.

| File | Owner | Exercises |
| --- | --- | --- |
| `acme-d01-harbor-partner-integration-runbook.docx` | Morgan Reed | Headings 1–3, nested numbered and bulleted lists, a table with a `\|` in a cell and a merged cell, a line break, page header and footer (should be skipped) |
| `acme-d02-beacon-q3-customer-health-review.docx` | Samira Cole | Meeting minutes: bullets, numbered decisions, two tables, an empty table cell |
| `acme-d03-office-equipment-request-guide.docx` | Nora Ellis | Bullets nested under a numbered list, a table, a line break, a Heading 4 |

## Facts to check after ingestion

- Harbor feeds down for more than 15 minutes go to the Harbor on-call engineer, with a 15-minute response target.
- Beacon ended Q3 2026 with 41 active accounts, 3 of them at risk.
- The external monitor allowance is $350, replaced every 5 years.

## Rebuilding

The files are generated from `tools/docx-sources/*.html` with LibreOffice, so they come from a real Word-compatible writer instead of the library the backend reads them with. After editing a source, run:

```bash
synthetic-data/tools/build_docx.sh
```
