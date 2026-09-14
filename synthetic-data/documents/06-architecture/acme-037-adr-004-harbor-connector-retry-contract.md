---
id: "ACME-037"
title: "ADR-004 Harbor Connector Retry Contract"
document_type: "architecture_decision"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-11"
updated: "2026-08-11"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-034", "ACME-041", "ACME-045"]
related_people: []
related_systems: ["Harbor"]
projects: []
---

# ADR-004 Harbor Connector Retry Contract

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Context
Harbor connects external business systems whose availability varies. Unlimited retries would hide failed integrations and consume capacity. Immediate failure would burden support with transient network errors.

## Decision
Permit at most five delivery attempts total: the initial attempt plus four retries. Wait 1, 2, 4, and 8 minutes after the first four failures. After the fifth failed attempt, send the event to the dead-letter queue and open an operational alert. There is no sixth automatic attempt.

## Error classes
Retry timeouts and temporary server failures. Authentication failures and invalid payloads require investigation instead of repeated identical attempts. The operator must correct the cause and authorize replay from the dead-letter queue. Replay preserves the event identity so tenant-scoped deduplication can prevent duplicate side effects.

## Observability
Record attempt number, correlation ID, outcome class, and time of next eligible attempt. Do not log secrets or full customer payloads in the operational event. Customer Success can explain the state using the correlation ID; it cannot infer delivery success from an empty source queue alone.

## Related documents
- [ACME-034 — ADR-001 Tenant-scoped Idempotent Delivery](acme-034-adr-001-tenant-scoped-idempotent-delivery.md)
- [ACME-041 — INC-2026-03 Harbor Connector Backlog](../07-incidents/acme-041-inc-2026-03-harbor-connector-backlog.md)
- [ACME-045 — Harbor Managed Integration Reference](../08-products/acme-045-harbor-managed-integration-reference.md)
