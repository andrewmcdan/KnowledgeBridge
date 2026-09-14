---
id: "ACME-010"
title: "Atlas Delivery Team and RACI"
document_type: "team_raci"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-21"
updated: "2026-08-21"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-006", "ACME-007", "ACME-015", "ACME-019", "ACME-023"]
related_people: ["Priya Shah", "Theo Park", "Jordan Blake", "Morgan Reed", "Imani Brooks", "Ravi Desai", "Nora Ellis", "Samira Cole"]
related_systems: ["Atlas"]
projects: ["Cedar"]
---

# Atlas Delivery Team and RACI

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Team purpose
The Atlas delivery team brings together Engineering, IT Operations, and Customer Success for workflow releases. Theo Park is the technical lead. Priya Shah sponsors Project Cedar; Jordan Blake is accountable for operational readiness. Membership does not change departmental reporting lines.

| Activity | Responsible | Accountable | Consulted |
| --- | --- | --- | --- |
| Workflow implementation | Theo Park | Priya Shah | Ravi Desai |
| Access configuration | Imani Brooks | Jordan Blake | Nora Ellis |
| Release readiness | Theo Park and Morgan Reed | Priya Shah and Jordan Blake | Samira Cole |
| Customer notice | Support lead | Samira Cole | Morgan Reed |

## Handoff rules
Theo provides regression results, schema compatibility, and rollback instructions. Morgan verifies monitoring and recovery readiness. Customer Success receives release notes before customer rollout. Joint accountability means both named approvers must sign the readiness record; it does not mean either one can approve alone.

## Exception handling
Unresolved tenant isolation findings block release. A missed target date should be recorded as a schedule change with an owner and mitigation. Do not treat a partial pilot as completion of Cedar's full rollout.

## Related documents
- [ACME-006 — Priya Shah Leadership Profile](acme-006-priya-shah-leadership-profile.md)
- [ACME-007 — Morgan Reed Operations Profile](acme-007-morgan-reed-operations-profile.md)
- [ACME-015 — Release and Rollback Procedure](../03-policies/acme-015-release-and-rollback-procedure.md)
- [ACME-019 — Project Cedar Charter](../04-projects/acme-019-project-cedar-charter.md)
- [ACME-023 — Cedar Status September 4](../04-projects/acme-023-cedar-status-september-4.md)
