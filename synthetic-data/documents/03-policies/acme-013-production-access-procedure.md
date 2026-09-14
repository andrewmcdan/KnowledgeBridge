---
id: "ACME-013"
title: "Production Access Procedure"
document_type: "access_procedure"
organization: "Acme Corporation"
owner: "Jordan Blake"
created: "2026-08-05"
updated: "2026-08-05"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-003", "ACME-007", "ACME-035", "ACME-047"]
related_people: []
related_systems: ["BridgeDesk"]
projects: []
---

# Production Access Procedure

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Request and grant
Open a BridgeDesk access request with the system, business reason, requested role, and expiry. The manager confirms business need and IT Operations approves the technical role. All production sessions use company SSO and MFA. Shared personal accounts are prohibited.

## Time-limited elevation
Elevated production access lasts at most four hours per grant. The expiry applies during incidents too. Incident commanders may accelerate review but must retain the ticket and reviewer record. If recovery takes longer, request a new grant with updated justification rather than extending access silently.

## Verification and revocation
The requester verifies only the actions required for the task. IT Operations records who granted the role, when it expires, and how access was removed. People Operations triggers immediate access removal for departures through the approved internal workflow. No employee departure details belong in this synthetic corpus.

## Review cadence
IT Operations reviews production membership monthly and records exceptions in BridgeDesk. Application role enforcement remains Engineering's responsibility. An application administrator role is not equivalent to database superuser or infrastructure administrator access. Production credentials and recovery codes must never be copied into tickets, documents, or knowledge-base uploads.

## Related documents
- [ACME-003 — IT Operations Service Ownership](../01-company/acme-003-it-operations-service-ownership.md)
- [ACME-007 — Morgan Reed Operations Profile](../02-people/acme-007-morgan-reed-operations-profile.md)
- [ACME-035 — ADR-002 Central Identity and Application Roles](../06-architecture/acme-035-adr-002-central-identity-and-application-roles.md)
- [ACME-047 — New Employee First Week Checklist](../09-training/acme-047-new-employee-first-week-checklist.md)
