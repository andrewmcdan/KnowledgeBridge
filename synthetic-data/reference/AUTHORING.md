# Acme v1 authoring baseline — do not ingest

This file records fixture design decisions and deliberate exceptions. It is not company knowledge and must not appear in retrieval results. Dataset scope is the 50 Markdown files listed in `catalog.json`, as of September 11, 2026. This is an initial authored draft for team review, not a claim of live ingestion or evaluated answer quality.

## Canonical entities

| Department | Employees | Lead |
| --- | ---: | --- |
| Engineering | 38 | Priya Shah |
| IT Operations | 12 | Jordan Blake |
| Customer Success | 24 | Samira Cole |
| Sales and Marketing | 20 | Avery Stone |
| Finance and Procurement | 14 | Elena Ortiz |
| People Operations | 12 | Nora Ellis |
| Total | 120 | Maya Chen, CEO |

Department totals are organizational baselines, not a complete employee roster. Leadership roles are included in the total, not added to it. `Company-wide` is a document scope label, not a seventh department. Ravi Desai leads Procurement under Elena; Morgan Reed is SRE lead and incident commander; Imani Brooks is the incident backup/access specialist; Theo Park leads Atlas; Leo Martin leads Beacon reporting.

| Name | Entity kind | Purpose |
| --- | --- | --- |
| Atlas | Service | Approval workflows |
| Beacon | Service | Operational analytics |
| Harbor | Service | Managed integrations |
| Advisory services | Service line | Fixed-scope consulting |
| Cedar | Active project | Atlas improvements |
| Aurora | Active project | Beacon regional reporting |
| Ledger | Active project | Vendor-record cleanup |
| Compass | Active project | Customer onboarding |
| Access Cleanup | Completed project | Test-account cleanup |
| BridgeDesk | System | Internal requests |
| ClientDesk | System | Customer support |
| ProcureFlow | System | Purchase approvals and vendor records |
| LedgerOne | System | General ledger |
| Software Budget / CC-410 | Cost center | Software expenditure |
| Nimbus Compute | Fictional vendor | Infrastructure subscription renewal |
| Pinecrest Services | Fictional customer | Compass pilot |

## Source authority and intentional exceptions

| Case | Source IDs | Expected interpretation |
| --- | --- | --- |
| Old procurement thresholds | 018 → 011 and 012 | Historical policy: full review above $2,500, extra CFO above $15,000. From August 1: above $5,000 and above $20,000 respectively. |
| Old Cedar forecast | 024 → 023; decision 026 | August snapshot: green, September 15. September snapshot: amber, September 22 subject to gates. |
| Unapproved CFO threshold | 028 versus 011/012 | The $10,000 meeting-note threshold is intentionally wrong; controlled policy wins despite the note being newer. |
| Draft Beacon retention | 044 versus 014; review 032 | Draft says 180 days; controlled rule is 365. Implementation compliance remains unverified. |
| Missing Aurora decisions | 020, 027, 038 | Pilot is conditional. No approved European hosting region, cross-region recovery target, or general-availability date. |

The status distribution is 43 current, two outdated, three incomplete, and two intentionally contradictory documents. A current document may accurately describe pending work: `current` does not mean every action it mentions is complete. The `incomplete` and `intentionally_contradictory` metadata labels deliberately make these starter cases discoverable.

## Stable arithmetic and chronology

- Four project envelopes: $48,000 + $32,000 + $12,000 + $8,000 = $100,000.
- September commitments: $29,600 + $9,600 + $6,200 + $4,600 = $50,000.
- Remaining project envelope: $50,000. Nimbus is separate: $24,000 ceiling minus $22,800 quote = $1,200 headroom.
- Vendor review: 36 candidate pairs; 20 reviewed = 14 approved + six rejected; 16 unreviewed. Approved merges are not evidence of completed merges.
- Login outage: July 9, 09:10–09:52 UTC = 42 minutes. Initial customer update at 09:22 is 12 minutes after impact began; do not infer a case-creation timestamp.
- Restore drill: 95 minutes observed versus 60-minute target, with 35 minutes spent resolving the stale credential reference. This was not a customer outage.
- Five Harbor attempts means one initial attempt plus four retries. Backoff totals 15 minutes excluding time spent executing attempts; the corpus does not promise a 15-minute delivery deadline.
- Cedar September 9/10 action due dates do not prove completion. No later acceptance record is included.

## Evaluation hygiene

Use `documents/` as the sole ingestion root. Keep this file, catalog facts, evaluation questions, and model outputs outside the indexed content. The 60 cases are intended for a baseline reliability exercise and are not a statistically independent training/test split. Do not tune prompts on every case and then report the same set as unseen evaluation.

Check required source IDs and factual entailment manually when scoring; alternate sufficient source combinations may deserve credit even if they differ from the initial answer key. For gap questions, relevant documents explain why knowledge is missing. They cannot prove absence from some future, larger corpus. Measure gaps against this versioned manifest.

This corpus is deliberately compact and text-only. It does not exercise binary extraction, OCR, very long-document truncation, multilingual retrieval, PII redaction, malicious instructions, or large-scale performance. Those should be separate named fixture packs rather than untracked changes to this baseline.
