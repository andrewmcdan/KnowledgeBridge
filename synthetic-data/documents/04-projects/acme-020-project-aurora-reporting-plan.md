---
id: "ACME-020"
title: "Project Aurora Reporting Plan"
document_type: "project_plan"
organization: "Acme Corporation"
owner: "Leo Martin"
created: "2026-08-28"
updated: "2026-08-28"
department: "Engineering"
status: "incomplete"
synthetic: true
related_documents: ["ACME-014", "ACME-027", "ACME-032", "ACME-038", "ACME-044"]
related_people: ["Priya Shah", "Leo Martin", "Jordan Blake"]
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# Project Aurora Reporting Plan

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Goal and owner
Project Aurora adds regional reporting to Beacon. Leo Martin is delivery lead and Priya Shah is sponsor. The planning envelope is $32,000. A limited pilot is targeted for October 6, 2026; the company has not approved a general-availability date.

## Proposed rollout
Stage one validates region tags in a non-production dataset. Stage two compares aggregate totals with the existing report. Stage three invites a small pilot after privacy and operational review. Customer data must not be copied into test fixtures to accelerate these checks.

## Open decisions
The hosting region for European customer data is undecided. No signed data-residency architecture is included. Audit-retention validation is also open because the controlled standard says 365 days while a draft Beacon guide says 180 days. Leo owns reconciling the guide with the implementation; Jordan Blake reviews the evidence.

## Exit gates
The pilot needs a documented region decision, retention evidence, and a customer communication plan. Until those gates close, sales material must describe Aurora as a planned pilot. A meeting attendee's preference for a region is not an approved hosting commitment.

## Related documents
- [ACME-014 — Data Retention Standard v2](../03-policies/acme-014-data-retention-standard-v2.md)
- [ACME-027 — Aurora Discovery Workshop](../05-meetings/acme-027-aurora-discovery-workshop.md)
- [ACME-032 — Security Retention Review August 29](../05-meetings/acme-032-security-retention-review-august-29.md)
- [ACME-038 — ADR-005 Beacon Regional Storage Decision Pending](../06-architecture/acme-038-adr-005-beacon-regional-storage-decision-pending.md)
- [ACME-044 — Beacon Administration Guide Draft](../08-products/acme-044-beacon-administration-guide-draft.md)
