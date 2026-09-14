---
id: "ACME-045"
title: "Harbor Managed Integration Reference"
document_type: "integration_reference"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-22"
updated: "2026-08-22"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-016", "ACME-030", "ACME-037", "ACME-041"]
related_people: []
related_systems: ["Harbor", "ClientDesk"]
projects: ["Compass"]
---

# Harbor Managed Integration Reference

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Service contract
Harbor moves business events between approved systems through managed connectors. Each event includes a tenant identifier, external event identifier, and correlation ID. Support uses the correlation ID to trace delivery without exposing the full payload. Credentials belong in the managed secret store, not the event body or documentation.

## Retry behavior
The standard contract permits five total attempts, including the initial attempt. After the first four failures, delays are 1, 2, 4, and 8 minutes. A fifth failure sends the event to the dead-letter queue. Operators correct the cause and authorize replay; the service does not silently attempt delivery forever.

## Support and onboarding
Customer Success receives cases in ClientDesk and routes operational failures to IT Operations. Compass includes one standard Harbor integration for Pinecrest Services. A custom payroll integration is outside that pilot scope and requires separate design and commercial review.

## Interpretation limits
Backlog count measures queued events, not affected customers. A record leaving the source queue is not proof of successful downstream business processing. Reconciliation must compare confirmed outcomes using event identity. The August backlog incident is a worked example of those distinctions.

## Related documents
- [ACME-016 — Customer Support Severity and Response](../03-policies/acme-016-customer-support-severity-and-response.md)
- [ACME-030 — Compass Pilot Kickoff](../05-meetings/acme-030-compass-pilot-kickoff.md)
- [ACME-037 — ADR-004 Harbor Connector Retry Contract](../06-architecture/acme-037-adr-004-harbor-connector-retry-contract.md)
- [ACME-041 — INC-2026-03 Harbor Connector Backlog](../07-incidents/acme-041-inc-2026-03-harbor-connector-backlog.md)
