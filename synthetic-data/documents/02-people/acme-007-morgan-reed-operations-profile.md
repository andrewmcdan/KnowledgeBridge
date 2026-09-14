---
id: "ACME-007"
title: "Morgan Reed Operations Profile"
document_type: "employee_profile"
organization: "Acme Corporation"
owner: "Jordan Blake"
created: "2026-08-20"
updated: "2026-08-20"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-003", "ACME-013", "ACME-015", "ACME-042"]
related_people: ["Morgan Reed", "Imani Brooks"]
related_systems: []
projects: ["Cedar"]
---

# Morgan Reed Operations Profile

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Role and scope
Morgan Reed is the SRE lead in IT Operations and the primary incident commander. Morgan coordinates severity assessment, restores service, maintains the incident timeline, and records the operational decision to roll back or continue recovery. Imani Brooks is the incident coordination backup.

## Evidence responsibilities
Morgan owns restore-test evidence: selected backup, test environment, measured recovery time, validation results, and cleanup confirmation. A dashboard showing a green backup job is insufficient for this sign-off. The restore drill in August found an obsolete credential reference despite successful scheduled backups.

## Team interfaces
Customer Success prepares customer-facing communications, with technical facts checked by the incident commander. Engineering owns application fixes and regression tests. Morgan can request an emergency rollback under the release procedure but cannot promise a customer a delivery date for an unapproved fix.

## Current work
Morgan is tracking the follow-up to INC-2026-04 and the Cedar release readiness checklist. Production access remains time-limited and ticketed, including when the requester is an incident responder. The profile intentionally omits real contact information, credentials, and personal employee details.

## Related documents
- [ACME-003 — IT Operations Service Ownership](../01-company/acme-003-it-operations-service-ownership.md)
- [ACME-013 — Production Access Procedure](../03-policies/acme-013-production-access-procedure.md)
- [ACME-015 — Release and Rollback Procedure](../03-policies/acme-015-release-and-rollback-procedure.md)
- [ACME-042 — INC-2026-04 Restore Drill Retrospective](../07-incidents/acme-042-inc-2026-04-restore-drill-retrospective.md)
