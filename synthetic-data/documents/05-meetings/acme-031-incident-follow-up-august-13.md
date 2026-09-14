---
id: "ACME-031"
title: "Incident Follow-up August 13"
document_type: "action_review"
organization: "Acme Corporation"
owner: "Morgan Reed"
created: "2026-08-13"
updated: "2026-08-13"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-023", "ACME-034", "ACME-040"]
related_people: ["Theo Park", "Morgan Reed", "Samira Cole"]
related_systems: []
projects: ["Cedar"]
---

# Incident Follow-up August 13

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Review context
Morgan Reed, Theo Park, and Samira Cole reviewed the duplicate approval notification incident, INC-2026-02, on August 13. The incident was mitigated, but its corrective action remained open pending regression evidence.

## Agreed behavior
The same event delivered repeatedly within a tenant must produce one customer notification. Two different tenants may legitimately use the same external event identifier. Therefore the idempotency key must include tenant identity; a global event identifier alone would incorrectly suppress valid work.

## Test and ownership
Theo owns a replay test that submits the same event three times for one tenant and once for another. Expected output is one notification per tenant. Morgan reviews operational metrics for suppressed duplicates and queue retries. Samira checks that the support explanation distinguishes repeated delivery from repeated approval.

## Closure rule
Attach test results to Cedar before closing the corrective action. Writing an architecture decision or merging code is not enough on its own. The current status report should continue to show the retry gate until a reviewer accepts the evidence. No completion date was certified in this review.

## Related documents
- [ACME-023 — Cedar Status September 4](../04-projects/acme-023-cedar-status-september-4.md)
- [ACME-034 — ADR-001 Tenant-scoped Idempotent Delivery](../06-architecture/acme-034-adr-001-tenant-scoped-idempotent-delivery.md)
- [ACME-040 — INC-2026-02 Duplicate Approval Notifications](../07-incidents/acme-040-inc-2026-02-duplicate-approval-notifications.md)
