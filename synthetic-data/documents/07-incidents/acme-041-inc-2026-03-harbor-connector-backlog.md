---
id: "ACME-041"
title: "INC-2026-03 Harbor Connector Backlog"
document_type: "incident_report"
organization: "Acme Corporation"
owner: "Morgan Reed"
created: "2026-08-18"
updated: "2026-08-18"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-016", "ACME-034", "ACME-037", "ACME-045"]
related_people: []
related_systems: ["Harbor"]
projects: []
---

# INC-2026-03 Harbor Connector Backlog

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Impact
On August 17, Harbor accumulated a peak backlog of 640 integration events after a vendor reduced its request-rate allowance. Customer dashboards lagged, but the source systems remained available. Support classified the incident P2 and recorded affected integrations by correlation ID.

## Diagnosis and recovery
The connector treated temporary rate limits as ordinary server failures and exhausted its permitted attempts faster than operators expected. Events reached the dead-letter queue after the fifth failed attempt. IT Operations adjusted the rate-limiting configuration, then authorized a controlled replay. Reconciliation found no lost events.

## What worked
The queue retained failed events and the replay preserved their identities. This allowed duplicate protection to distinguish a replay from a new business event. Support could explain delayed processing without falsely claiming that every item had already reached its destination.

## Corrective work
Engineering will classify vendor rate-limit responses explicitly, and IT Operations will alert on backlog age as well as event count. This report does not change the five-attempt contract. A higher retry limit requires a reviewed decision rather than an incident-time assumption embedded in documentation.

## Related documents
- [ACME-016 — Customer Support Severity and Response](../03-policies/acme-016-customer-support-severity-and-response.md)
- [ACME-034 — ADR-001 Tenant-scoped Idempotent Delivery](../06-architecture/acme-034-adr-001-tenant-scoped-idempotent-delivery.md)
- [ACME-037 — ADR-004 Harbor Connector Retry Contract](../06-architecture/acme-037-adr-004-harbor-connector-retry-contract.md)
- [ACME-045 — Harbor Managed Integration Reference](../08-products/acme-045-harbor-managed-integration-reference.md)
