---
id: "ACME-004"
title: "Finance and Procurement Operating Model"
document_type: "operating_model"
organization: "Acme Corporation"
owner: "Elena Ortiz"
created: "2026-08-15"
updated: "2026-08-15"
department: "Finance and Procurement"
status: "current"
synthetic: true
related_documents: ["ACME-008", "ACME-011", "ACME-012", "ACME-049", "ACME-050"]
related_people: ["Elena Ortiz", "Ravi Desai"]
related_systems: ["ProcureFlow", "LedgerOne"]
projects: []
---

# Finance and Procurement Operating Model

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Responsibilities
Finance and Procurement is one department with 14 employees. Elena Ortiz, CFO, is accountable for budgets, expense controls, and delegated financial approval. Ravi Desai leads the Procurement team within the department. Finance validates funding and expense classification; Procurement negotiates terms and issues purchase orders.

## Records and systems
LedgerOne stores the general ledger. ProcureFlow records requests, approvals, vendor reviews, and purchase orders. Software Budget is cost center CC-410. Project funding references must include the project name as well as the cost center because several projects share CC-410.

## Decision controls
The requester supplies the annual committed cost, not just the first monthly invoice. A manager confirms business need. Finance confirms affordability. IT Operations checks software suitability, and Procurement checks terms. Additional CFO approval applies only at the threshold in the current approval matrix. An approved budget does not, by itself, authorize a purchase.

## Handoffs
Invoices without a purchase-order reference are returned for investigation rather than automatically paid. Contract originals belong in the vendor record, while customer deliverables belong in the appropriate project workspace. Policy changes require a version and an effective date so old meeting notes cannot silently override current controls.

## Related documents
- [ACME-008 — Ravi Desai Procurement Profile](../02-people/acme-008-ravi-desai-procurement-profile.md)
- [ACME-011 — Procurement Policy v2.1](../03-policies/acme-011-procurement-policy-v2-1.md)
- [ACME-012 — Finance Approval Matrix 2026](../03-policies/acme-012-finance-approval-matrix-2026.md)
- [ACME-049 — Nimbus Compute Renewal Assessment](../10-finance-vendors/acme-049-nimbus-compute-renewal-assessment.md)
- [ACME-050 — September Project Budget Register](../10-finance-vendors/acme-050-september-project-budget-register.md)
