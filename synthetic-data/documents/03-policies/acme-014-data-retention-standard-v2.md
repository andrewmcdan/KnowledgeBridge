---
id: "ACME-014"
title: "Data Retention Standard v2"
document_type: "retention_policy"
organization: "Acme Corporation"
owner: "Jordan Blake"
created: "2026-08-10"
updated: "2026-08-10"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-038", "ACME-044", "ACME-020", "ACME-032"]
related_people: []
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# Data Retention Standard v2

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Controlled retention schedule
This standard takes effect August 10, 2026. Application audit events are retained for 365 days from event time. Support attachments are retained for 90 days after the support case closes. Operational backups are retained for 30 days from backup creation. These are distinct record classes; a shorter backup window does not reduce the audit-event obligation.

## Deletion workflow
The owning service marks records eligible when their retention clock expires. A scheduled job removes eligible records and produces a deletion-count report. Customer Success confirms case closure, Engineering owns application deletion jobs, and IT Operations owns backup expiry. Monthly sample checks compare stored timestamps with the eligibility rule.

## Exceptions and scope
A documented hold pauses deletion for the records explicitly covered by that hold. This corpus defines no actual legal hold or jurisdiction-specific retention requirement. Do not infer one. Free-text product notes do not approve an exception to this standard.

## Implementation gap
Beacon's draft administration guide still mentions 180-day audit retention. That is a known documentation conflict, not an alternate approved schedule. The Beacon owner must reconcile the guide and validate its implementation against this controlled standard before the Aurora rollout.

## Related documents
- [ACME-038 — ADR-005 Beacon Regional Storage Decision Pending](../06-architecture/acme-038-adr-005-beacon-regional-storage-decision-pending.md)
- [ACME-044 — Beacon Administration Guide Draft](../08-products/acme-044-beacon-administration-guide-draft.md)
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-032 — Security Retention Review August 29](../05-meetings/acme-032-security-retention-review-august-29.md)
