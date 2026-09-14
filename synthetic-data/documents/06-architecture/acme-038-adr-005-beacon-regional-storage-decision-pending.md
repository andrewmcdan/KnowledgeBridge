---
id: "ACME-038"
title: "ADR-005 Beacon Regional Storage Decision Pending"
document_type: "architecture_proposal"
organization: "Acme Corporation"
owner: "Leo Martin"
created: "2026-08-30"
updated: "2026-08-30"
department: "Engineering"
status: "incomplete"
synthetic: true
related_documents: ["ACME-020", "ACME-027", "ACME-032", "ACME-044"]
related_people: ["Leo Martin", "Jordan Blake"]
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# ADR-005 Beacon Regional Storage Decision Pending

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Status: proposed
This record captures an unresolved architecture choice for Aurora regional reporting. It is not an accepted design. Leo Martin owns the proposal and Jordan Blake owns operational review. A decision date has not been approved.

## Options under review
The team is comparing retaining the current shared deployment with adding a separately operated regional deployment. The trade-offs include data movement, support access, restore complexity, and reporting consistency. Naming an option does not approve its hosting location or establish contractual residency.

## Required evidence
Before acceptance, document customer requirements, region-specific storage behavior, operational ownership, and a tested recovery arrangement. Reconcile Beacon's draft 180-day audit guidance with the controlled 365-day standard. Test data must be synthetic and must exercise events near retention boundaries.

## Unanswered questions
The physical European hosting region, cross-region recovery target, and migration sequence remain unspecified. No vendor quote in this corpus resolves those decisions. When asked where European customer data will be stored, describe the proposal status and the missing decision; do not guess a common cloud region from product branding or workshop remarks.

## Related documents
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-027 — Aurora Discovery Workshop](../05-meetings/acme-027-aurora-discovery-workshop.md)
- [ACME-032 — Security Retention Review August 29](../05-meetings/acme-032-security-retention-review-august-29.md)
- [ACME-044 — Beacon Administration Guide Draft](../08-products/acme-044-beacon-administration-guide-draft.md)
