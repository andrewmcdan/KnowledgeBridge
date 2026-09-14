---
id: "ACME-002"
title: "Engineering Department Charter"
document_type: "department_charter"
organization: "Acme Corporation"
owner: "Priya Shah"
created: "2026-08-12"
updated: "2026-08-12"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-003", "ACME-010", "ACME-019", "ACME-020", "ACME-040"]
related_people: ["Priya Shah", "Theo Park", "Leo Martin"]
related_systems: ["Atlas", "Beacon"]
projects: ["Cedar", "Aurora"]
---

# Engineering Department Charter

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Mandate
Engineering builds and maintains Acme's applications, integration adapters, and automated release tests. The department has 38 employees and is led by Priya Shah. Product teams propose implementation options; Priya approves engineering priorities within the funded quarterly plan. Theo Park is technical lead for Atlas, while Leo Martin leads Beacon's data model work.

## Ownership boundaries
Engineering owns application code, schema migrations, and service-level instrumentation. IT Operations administers production identities, deployment infrastructure, backups, and on-call coordination. A production release requires an engineering owner and an IT Operations reviewer; neither department may silently transfer its approval to the other.

## Working agreements
Every change needs a tracked request, a reviewer other than its author, a rollback note, and test evidence proportionate to risk. A documentation-only change does not require a customer maintenance window. Changes to tenant boundaries or data retention require an architecture decision record and security review.

## Current priorities
Cedar stabilizes Atlas approval workflows. Aurora introduces Beacon regional reporting. Engineering's September allocation reserves one day per sprint for incident follow-up, including the duplicate-delivery remediation from INC-2026-02. Feature deadlines do not waive unresolved release gates.

## Related documents
- [ACME-003 — IT Operations Service Ownership](acme-003-it-operations-service-ownership.md)
- [ACME-010 — Atlas Delivery Team and RACI](../02-people/acme-010-atlas-delivery-team-and-raci.md)
- [ACME-019 — Project Cedar Charter](../04-projects/acme-019-project-cedar-charter.md)
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-040 — INC-2026-02 Duplicate Approval Notifications](../07-incidents/acme-040-inc-2026-02-duplicate-approval-notifications.md)
