---
id: "ACME-016"
title: "Customer Support Severity and Response"
document_type: "support_procedure"
organization: "Acme Corporation"
owner: "Samira Cole"
created: "2026-08-14"
updated: "2026-08-14"
department: "Customer Success"
status: "current"
synthetic: true
related_documents: ["ACME-009", "ACME-003", "ACME-039", "ACME-045"]
related_people: []
related_systems: ["ClientDesk"]
projects: []
---

# Customer Support Severity and Response

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Severity classification
P1 means a production outage affecting multiple customers or a suspected cross-tenant data exposure. P2 means a major function is impaired but a workable alternative exists. P3 covers routine questions and minor defects. Customer Success assigns an initial severity and revises it when evidence changes.

| Severity | Initial response target | Update cadence |
| --- | --- | --- |
| P1 | 15 minutes, 24/7 | Every 30 minutes until stabilized |
| P2 | Four business hours | Once per business day |
| P3 | One business day | At meaningful progress or closure |

## Clock and ownership
The clock starts at the ClientDesk case creation time. Business hours are Monday–Friday, 09:00–17:00 UTC, excluding the published company holiday calendar. No holiday calendar is included in this corpus, so calculations across a possible holiday require clarification.

## Customer communication
Samira's team sends customer updates; Morgan or the acting incident commander checks technical facts. State known impact, current mitigation, and next update time. Do not invent a root cause or give a resolution guarantee. An initial response target is not a recovery-time objective and is not a promise of contractual service credit.

## Related documents
- [ACME-009 — Samira Cole Customer Success Profile](../02-people/acme-009-samira-cole-customer-success-profile.md)
- [ACME-003 — IT Operations Service Ownership](../01-company/acme-003-it-operations-service-ownership.md)
- [ACME-039 — INC-2026-01 Atlas Login Outage](../07-incidents/acme-039-inc-2026-01-atlas-login-outage.md)
- [ACME-045 — Harbor Managed Integration Reference](../08-products/acme-045-harbor-managed-integration-reference.md)
