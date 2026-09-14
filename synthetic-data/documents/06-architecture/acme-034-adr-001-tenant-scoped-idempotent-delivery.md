---
id: "ACME-034"
title: "ADR-001 Tenant-scoped Idempotent Delivery"
document_type: "architecture_decision"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-14"
updated: "2026-08-14"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-031", "ACME-040", "ACME-043"]
related_people: []
related_systems: ["Atlas"]
projects: []
---

# ADR-001 Tenant-scoped Idempotent Delivery

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Context
Atlas receives at-least-once event delivery from its queue. INC-2026-02 showed that retries could create duplicate customer notifications. Replacing the queue would be disproportionate and would not remove every source of duplicate delivery.

## Decision
Use a persistent idempotency record keyed by tenant ID and external event ID before generating a notification. A repeated key returns the recorded outcome without producing another notification. Distinct tenants with the same external event ID remain independent. Record processing outcome and a correlation identifier for support.

## Consequences
Transport is still at least once. Do not advertise an exactly-once transport guarantee. The deduplication operation and notification state change must be coordinated so a crash cannot leave an untracked side effect. Engineering owns the implementation and replay tests; IT Operations monitors retries and suppressed duplicate counts.

## Verification
The acceptance scenario replays one event three times within a tenant and once in a second tenant, expecting two notifications total. The decision does not prescribe the retention duration of idempotency records; that duration needs a separate operational decision before long-range replay guarantees can be stated.

## Related documents
- [ACME-031 — Incident Follow-up August 13](../05-meetings/acme-031-incident-follow-up-august-13.md)
- [ACME-040 — INC-2026-02 Duplicate Approval Notifications](../07-incidents/acme-040-inc-2026-02-duplicate-approval-notifications.md)
- [ACME-043 — Atlas Workflow Automation Service Guide](../08-products/acme-043-atlas-workflow-automation-service-guide.md)
