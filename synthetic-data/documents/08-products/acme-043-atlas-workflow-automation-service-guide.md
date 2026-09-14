---
id: "ACME-043"
title: "Atlas Workflow Automation Service Guide"
document_type: "product_guide"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-21"
updated: "2026-08-21"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-019", "ACME-023", "ACME-030", "ACME-034"]
related_people: []
related_systems: ["Atlas"]
projects: ["Cedar", "Compass"]
---

# Atlas Workflow Automation Service Guide

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Service capabilities
Atlas routes business requests through sequential approval steps and retains an audit history of decisions. A customer administrator configures request fields and approver groups within the supported model. Finance and Procurement use an Atlas demonstration workflow to illustrate software purchase review.

## Operating behavior
An approval decision and its notification are separate records. Notification delivery may be retried; tenant-scoped idempotency prevents repeated delivery from creating another notification. The service should not be described as an exactly-once transport. A pending request is not an approved purchase order.

## Limits and exclusions
Customer-defined scripting, a dedicated mobile application, and replacement of external finance systems are outside the current service scope. The guide defines no public pricing table or unlimited transaction allowance. Capacity promises must come from an approved commercial agreement.

## Related work
Project Cedar improves duplicate-event protection, approval-history clarity, and rollback readiness. Its September rollout remains subject to the current status report's gates. Compass uses one Atlas workflow in the Pinecrest pilot, with acceptance handled by Customer Success rather than inferred from a successful configuration save.

## Related documents
- [ACME-019 — Project Cedar Charter](../04-projects/acme-019-project-cedar-charter.md)
- [ACME-023 — Cedar Status September 4](../04-projects/acme-023-cedar-status-september-4.md)
- [ACME-030 — Compass Pilot Kickoff](../05-meetings/acme-030-compass-pilot-kickoff.md)
- [ACME-034 — ADR-001 Tenant-scoped Idempotent Delivery](../06-architecture/acme-034-adr-001-tenant-scoped-idempotent-delivery.md)
