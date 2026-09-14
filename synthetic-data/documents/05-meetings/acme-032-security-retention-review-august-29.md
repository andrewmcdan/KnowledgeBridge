---
id: "ACME-032"
title: "Security Retention Review August 29"
document_type: "security_review"
organization: "Acme Corporation"
owner: "Jordan Blake"
created: "2026-08-29"
updated: "2026-08-29"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-014", "ACME-020", "ACME-038", "ACME-044"]
related_people: ["Leo Martin", "Jordan Blake"]
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# Security Retention Review August 29

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Review scope
Jordan Blake and Leo Martin compared the Data Retention Standard v2 with Beacon's draft administration guide on August 29. The standard requires 365-day application audit retention. The draft guide states 180 days. The mismatch affects Aurora readiness because regional reporting must not launch on an unverified retention assumption.

## Decision
The controlled standard governs. The meeting did not approve a 180-day exception. Leo must correct or explicitly retire the draft guidance and attach evidence showing the implemented retention rule. Jordan will review that evidence as an operational gate.

## Evidence requested
Capture the configuration source, a sample of audit-event timestamps, the eligibility calculation, and the deletion-job test result. A documentation edit alone does not prove implementation compliance. Do not use production customer records as test fixtures; construct synthetic timestamps around the retention boundary.

## Status
The action remained open at meeting close. No later closure record is included as of September 11. An answer can state the required schedule and the known conflict, but should not assert that Beacon has already been fixed. This distinction is important when reporting policy compliance versus intended behavior.

## Related documents
- [ACME-014 — Data Retention Standard v2](../03-policies/acme-014-data-retention-standard-v2.md)
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-038 — ADR-005 Beacon Regional Storage Decision Pending](../06-architecture/acme-038-adr-005-beacon-regional-storage-decision-pending.md)
- [ACME-044 — Beacon Administration Guide Draft](../08-products/acme-044-beacon-administration-guide-draft.md)
