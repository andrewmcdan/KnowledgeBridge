---
id: "ACME-044"
title: "Beacon Administration Guide Draft"
document_type: "draft_product_guide"
organization: "Acme Corporation"
owner: "Leo Martin"
created: "2026-08-23"
updated: "2026-08-23"
department: "Engineering"
status: "intentionally_contradictory"
synthetic: true
related_documents: ["ACME-014", "ACME-020", "ACME-032", "ACME-038"]
related_people: ["Leo Martin"]
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# Beacon Administration Guide Draft

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Draft status
This guide is an unapproved working draft for Beacon administration. Beacon provides aggregate operational reporting from connected business systems. Report region tags identify business groupings; they do not establish the physical location of stored data.

## Draft retention wording
The current draft text states: 'Application audit events are retained for 180 days.' This wording conflicts with Data Retention Standard v2, which requires 365 days. The discrepancy is retained here for review and has not been accepted as an exception.

## Administration workflow
Administrators configure approved data sources, validate field mappings against a sample, and review aggregate totals before publishing a report. A blank chart is not proof that there are no business events; the source connection and refresh timestamp must also be checked. Do not use real customer records as demonstration test fixtures.

## Pending corrections
Leo Martin owns reconciliation of this guide and implementation evidence. The August 29 security review confirms that the controlled standard governs. Aurora's regional pilot remains conditional on resolving the retention issue and an accepted storage-region decision. This draft establishes neither current compliance nor a committed European hosting location.

## Related documents
- [ACME-014 — Data Retention Standard v2](../03-policies/acme-014-data-retention-standard-v2.md)
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-032 — Security Retention Review August 29](../05-meetings/acme-032-security-retention-review-august-29.md)
- [ACME-038 — ADR-005 Beacon Regional Storage Decision Pending](../06-architecture/acme-038-adr-005-beacon-regional-storage-decision-pending.md)
