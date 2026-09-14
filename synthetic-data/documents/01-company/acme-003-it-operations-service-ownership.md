---
id: "ACME-003"
title: "IT Operations Service Ownership"
document_type: "service_ownership"
organization: "Acme Corporation"
owner: "Jordan Blake"
created: "2026-08-20"
updated: "2026-08-20"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-007", "ACME-013", "ACME-015", "ACME-042"]
related_people: ["Jordan Blake", "Morgan Reed", "Imani Brooks"]
related_systems: ["BridgeDesk"]
projects: []
---

# IT Operations Service Ownership

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Service remit
IT Operations is a 12-person department led by Jordan Blake. It owns the company identity service, production infrastructure, backup schedules, access reviews, and incident coordination. The team runs BridgeDesk for internal requests and receives customer incidents through Customer Success.

| Service | Accountable owner | Operational contact |
| --- | --- | --- |
| Production access and SSO | Jordan Blake | Imani Brooks |
| Backup and restore | Jordan Blake | Morgan Reed |
| Incident coordination | Morgan Reed | Imani Brooks |

## Escalation practice
Morgan is the primary incident commander; Imani is the backup. An unavailable primary does not delay severity assignment or customer notification. Customer Success owns the outward-facing update, while the incident commander verifies its technical facts. Engineering owns code-level fixes and supplies a rollback assessment.

## Service boundaries
Routine software purchase approval remains with Finance and Procurement. IT Operations reviews security, licensing, and integration fit but cannot authorize spending or sign a vendor agreement. Project owners must book restore tests before rollout; a successful backup job is not evidence of a successful restore.

## Related documents
- [ACME-007 — Morgan Reed Operations Profile](../02-people/acme-007-morgan-reed-operations-profile.md)
- [ACME-013 — Production Access Procedure](../03-policies/acme-013-production-access-procedure.md)
- [ACME-015 — Release and Rollback Procedure](../03-policies/acme-015-release-and-rollback-procedure.md)
- [ACME-042 — INC-2026-04 Restore Drill Retrospective](../07-incidents/acme-042-inc-2026-04-restore-drill-retrospective.md)
