---
id: "ACME-036"
title: "ADR-003 Backward-compatible Database Changes"
document_type: "architecture_decision"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-19"
updated: "2026-08-19"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-015", "ACME-019", "ACME-042"]
related_people: []
related_systems: []
projects: ["Cedar"]
---

# ADR-003 Backward-compatible Database Changes

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Context
Application rollback is unsafe when a deployment immediately removes a column required by the previous version. Cedar's workflow changes need a migration strategy compatible with the operational rollback procedure.

## Decision
Use expand-and-contract changes. Add the new representation first, deploy code that can tolerate both representations, backfill with validation, and defer destructive cleanup until the rollback window is formally closed. Migration versions belong with the application release record and must be applied in a controlled order.

## Review evidence
Engineering provides compatibility tests for the current and previous application versions. IT Operations reviews backup and restore readiness. A successful schema command does not replace application transaction validation. Backfills must be resumable and report counts so an interruption can be distinguished from data loss.

## Consequences
Temporary duplicated fields increase implementation complexity but preserve a practical rollback path. The team must track cleanup as explicit follow-up work instead of leaving it indefinitely. A data correction that cannot be rolled back requires a separately reviewed recovery plan and must not be hidden inside an ordinary application deployment.

## Related documents
- [ACME-015 — Release and Rollback Procedure](../03-policies/acme-015-release-and-rollback-procedure.md)
- [ACME-019 — Project Cedar Charter](../04-projects/acme-019-project-cedar-charter.md)
- [ACME-042 — INC-2026-04 Restore Drill Retrospective](../07-incidents/acme-042-inc-2026-04-restore-drill-retrospective.md)
