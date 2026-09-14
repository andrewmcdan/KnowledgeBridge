---
id: "ACME-039"
title: "INC-2026-01 Atlas Login Outage"
document_type: "incident_report"
organization: "Acme Corporation"
owner: "Morgan Reed"
created: "2026-07-10"
updated: "2026-07-10"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-003", "ACME-013", "ACME-016", "ACME-035"]
related_people: []
related_systems: ["Atlas"]
projects: []
---

# INC-2026-01 Atlas Login Outage

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Impact and timeline
On July 9, 2026, Atlas new logins failed from 09:10 to 09:52 UTC, a 42-minute interruption. Existing sessions remained usable. The incident was classified P1 because multiple customer workspaces could not start new sessions. Customer Success sent the first customer update at 09:22 UTC.

## Cause and mitigation
The SSO signing certificate had expired. Morgan coordinated recovery while Imani updated the configured certificate through the approved access workflow. New login validation passed at 09:52. The team did not disable MFA or grant shared accounts as a workaround.

## Corrective actions
IT Operations added certificate-expiry alerts at 30, 14, and seven days before expiry, assigned an owner to the renewal record, and scheduled a quarterly login-path check. Engineering added a clearer dependency error to the login health view. Alert creation and alert delivery must both be tested.

## Scope limits
No evidence of customer data exposure was found in the incident review. That statement is specific to this review, not a general security guarantee. The response target and the actual restoration duration are different measures; a 15-minute initial-response target does not imply service had to be restored within 15 minutes.

## Related documents
- [ACME-003 — IT Operations Service Ownership](../01-company/acme-003-it-operations-service-ownership.md)
- [ACME-013 — Production Access Procedure](../03-policies/acme-013-production-access-procedure.md)
- [ACME-016 — Customer Support Severity and Response](../03-policies/acme-016-customer-support-severity-and-response.md)
- [ACME-035 — ADR-002 Central Identity and Application Roles](../06-architecture/acme-035-adr-002-central-identity-and-application-roles.md)
