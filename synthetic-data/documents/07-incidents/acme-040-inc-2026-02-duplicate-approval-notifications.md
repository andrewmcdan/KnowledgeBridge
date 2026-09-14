---
id: "ACME-040"
title: "INC-2026-02 Duplicate Approval Notifications"
document_type: "incident_report"
organization: "Acme Corporation"
owner: "Theo Park"
created: "2026-08-12"
updated: "2026-08-12"
department: "Engineering"
status: "current"
synthetic: true
related_documents: ["ACME-016", "ACME-023", "ACME-031", "ACME-034"]
related_people: []
related_systems: ["Atlas"]
projects: ["Cedar"]
---

# INC-2026-02 Duplicate Approval Notifications

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Incident summary
On August 11, Atlas produced 27 duplicate approval notifications across nine fictional customer workspaces. The underlying approval records were not duplicated. Customer Success classified the event as P2 because the core approval workflow remained usable and the duplicate message could be identified.

## Cause
A queue retry repeated notification generation after a worker timeout. The worker had no persistent tenant-scoped idempotency check around that side effect. The fact that the source event carried an identifier did not itself prevent duplicate output.

## Mitigation
Engineering paused the affected notification worker, reconciled notification IDs with approval records, and resumed processing under supervised replay. Customer Success told affected pilot administrators that the extra messages did not represent additional approvals. No production record deletion was used as a shortcut to reconciliation.

## Follow-up
ADR-001 specifies tenant ID plus external event ID for duplicate suppression. Theo owns replay regression; Morgan owns monitoring review. The August 13 action review and Cedar's September status still track accepted test evidence as a release gate. Do not report the corrective action closed merely because the incident's immediate impact ended.

## Related documents
- [ACME-016 — Customer Support Severity and Response](../03-policies/acme-016-customer-support-severity-and-response.md)
- [ACME-023 — Cedar Status September 4](../04-projects/acme-023-cedar-status-september-4.md)
- [ACME-031 — Incident Follow-up August 13](../05-meetings/acme-031-incident-follow-up-august-13.md)
- [ACME-034 — ADR-001 Tenant-scoped Idempotent Delivery](../06-architecture/acme-034-adr-001-tenant-scoped-idempotent-delivery.md)
