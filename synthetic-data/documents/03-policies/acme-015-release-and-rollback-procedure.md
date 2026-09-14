---
id: "ACME-015"
title: "Release and Rollback Procedure"
document_type: "runbook"
organization: "Acme Corporation"
owner: "Morgan Reed"
created: "2026-08-19"
updated: "2026-08-19"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-010", "ACME-036", "ACME-039", "ACME-042"]
related_people: []
related_systems: ["Atlas", "Beacon"]
projects: []
---

# Release and Rollback Procedure

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Before the window
Routine production changes use Tuesday or Thursday windows, 18:00–20:00 UTC. The change record includes test evidence, migration compatibility, monitoring links, rollback steps, and a named operator. Engineering and IT Operations both sign off. Customer Success receives the approved release note before a customer-facing rollout.

## Release sequence
Take the pre-change recovery checkpoint, deploy to the canary group, verify business transactions, and compare error rate with baseline. Expand only after the canary check is accepted. A green deployment command does not prove the application works. Test an actual approval transaction for Atlas or a report retrieval for Beacon.

## Stop and rollback
Begin rollback when the application error rate exceeds 2% for five consecutive minutes, or immediately when tenant isolation is breached. The incident commander may stop earlier if evidence indicates customer data risk. Record the rollback start time and validate restored business behavior before closing the change.

## Aftercare
Keep observation active for 30 minutes after rollout or rollback. Attach results to the change record and create follow-up work for any unexpected manual step. An emergency release still needs named reviewers and retrospective evidence; urgency changes timing, not accountability.

## Related documents
- [ACME-010 — Atlas Delivery Team and RACI](../02-people/acme-010-atlas-delivery-team-and-raci.md)
- [ACME-036 — ADR-003 Backward-compatible Database Changes](../06-architecture/acme-036-adr-003-backward-compatible-database-changes.md)
- [ACME-039 — INC-2026-01 Atlas Login Outage](../07-incidents/acme-039-inc-2026-01-atlas-login-outage.md)
- [ACME-042 — INC-2026-04 Restore Drill Retrospective](../07-incidents/acme-042-inc-2026-04-restore-drill-retrospective.md)
