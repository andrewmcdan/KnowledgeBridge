---
id: "ACME-012"
title: "Finance Approval Matrix 2026"
document_type: "approval_matrix"
organization: "Acme Corporation"
owner: "Elena Ortiz"
created: "2026-08-01"
updated: "2026-08-01"
department: "Finance and Procurement"
status: "current"
synthetic: true
related_documents: ["ACME-011", "ACME-004", "ACME-049"]
related_people: []
related_systems: []
projects: []
---

# Finance Approval Matrix 2026

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Authority table
Effective August 1, 2026; amounts are USD annual committed software cost. Apply Procurement Policy v2.1's funding check and sequencing rules.

| Amount | Business and funding approval | Additional review |
| --- | --- | --- |
| At or below $5,000 | Manager and Finance | IT Operations for any new software |
| Above $5,000 through $20,000 | Manager and Finance | IT Operations and Procurement |
| Above $20,000 | Manager, Finance, and CFO or documented delegate | IT Operations and Procurement |

## Boundary examples
A $5,000 renewal of already-approved software is in the first band. A $5,000.01 request enters the full review band. A $20,000 purchase is still in the middle band; $20,000.01 requires CFO approval. A $1,900 monthly commitment for twelve months is $22,800 and therefore needs CFO approval.

## Delegation and controls
This matrix does not name a standing CFO delegate. A delegation must identify scope, amount limit, and validity period in the request record. Requesters cannot approve their own purchases. Finance checks the full commitment against the cost center, and Procurement retains the final purchase-order reference. Advisory-service agreements use their signed statement of work, not these software-only bands.

## Related documents
- [ACME-011 — Procurement Policy v2.1](acme-011-procurement-policy-v2-1.md)
- [ACME-004 — Finance and Procurement Operating Model](../01-company/acme-004-finance-and-procurement-operating-model.md)
- [ACME-049 — Nimbus Compute Renewal Assessment](../10-finance-vendors/acme-049-nimbus-compute-renewal-assessment.md)
