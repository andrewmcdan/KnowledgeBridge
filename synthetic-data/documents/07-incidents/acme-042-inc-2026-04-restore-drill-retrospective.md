---
id: "ACME-042"
title: "INC-2026-04 Restore Drill Retrospective"
document_type: "drill_retrospective"
organization: "Acme Corporation"
owner: "Morgan Reed"
created: "2026-08-28"
updated: "2026-08-28"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-003", "ACME-007", "ACME-015", "ACME-023", "ACME-026"]
related_people: []
related_systems: []
projects: ["Cedar"]
---

# INC-2026-04 Restore Drill Retrospective

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Exercise result
The August 27 restore drill took 95 minutes against a 60-minute recovery target. It used synthetic data in an isolated test environment and caused no customer outage. Scheduled backup jobs had been succeeding, but that success did not guarantee the instructions could restore service within target.

## Failure mechanism
The runbook referenced an obsolete credential location. Operators spent 35 minutes resolving the access problem before the restore could proceed. The backup itself was readable, and the subsequent business-transaction validation passed. No credential value is reproduced in this report.

## Follow-up
Morgan owns updating the runbook to use the managed secret alias, validating that the incident role can resolve it, and repeating the exercise. Jordan reviews the sign-off. Cedar retains a rollback-readiness gate until this evidence is accepted. An assigned due date is not proof the repeat test succeeded.

## Lessons
Test the complete recovery path, including identities and dependencies. Compare measured restore time with the target using the same start and end definitions. Do not describe the 95-minute observation as a new approved target, and do not describe this test failure as a production service outage.

## Related documents
- [ACME-003 — IT Operations Service Ownership](../01-company/acme-003-it-operations-service-ownership.md)
- [ACME-007 — Morgan Reed Operations Profile](../02-people/acme-007-morgan-reed-operations-profile.md)
- [ACME-015 — Release and Rollback Procedure](../03-policies/acme-015-release-and-rollback-procedure.md)
- [ACME-023 — Cedar Status September 4](../04-projects/acme-023-cedar-status-september-4.md)
- [ACME-026 — Cedar Steering September 3](../05-meetings/acme-026-cedar-steering-september-3.md)
