---
id: "ACME-021"
title: "Project Ledger Vendor Cleanup Plan"
document_type: "project_plan"
organization: "Acme Corporation"
owner: "Ravi Desai"
created: "2026-08-25"
updated: "2026-08-25"
department: "Finance and Procurement"
status: "current"
synthetic: true
related_documents: ["ACME-008", "ACME-029", "ACME-033", "ACME-049", "ACME-050"]
related_people: ["Elena Ortiz", "Ravi Desai"]
related_systems: ["ProcureFlow", "LedgerOne"]
projects: ["Ledger"]
---

# Project Ledger Vendor Cleanup Plan

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Objective
Project Ledger cleans vendor reference data in ProcureFlow and reconciles it with LedgerOne. Ravi Desai leads the work; Elena Ortiz sponsors it. The project envelope is $12,000. The baseline inventory contains 240 vendor records and 36 possible duplicate pairs, not 36 confirmed duplicate vendors.

## Method
Normalize names and tax-reference placeholders in a staging report, then ask Finance to review each proposed pair. No automatic production merges are authorized. Keep the surviving identifier, source identifiers, reviewer, and reason in the merge log. Historical purchase-order references must remain resolvable.

## Acceptance criteria
Every candidate pair has a recorded decision; all approved merges retain transaction history; and a sample of ten purchase orders resolves to the intended vendor. A declined merge counts as reviewed, not as an unresolved failure. There is no target to force the vendor count down by a specific amount.

## Dependencies
The Nimbus renewal must use the reviewed vendor record before commitment. The project must not store bank-account data in the knowledge base. The August Finance review sets the control requirements, and the September operations review reports progress separately from the initial baseline.

## Related documents
- [ACME-008 — Ravi Desai Procurement Profile](../02-people/acme-008-ravi-desai-procurement-profile.md)
- [ACME-029 — Finance Budget Review August 12](../05-meetings/acme-029-finance-budget-review-august-12.md)
- [ACME-033 — Weekly Operations Review September 8](../05-meetings/acme-033-weekly-operations-review-september-8.md)
- [ACME-049 — Nimbus Compute Renewal Assessment](../10-finance-vendors/acme-049-nimbus-compute-renewal-assessment.md)
- [ACME-050 — September Project Budget Register](../10-finance-vendors/acme-050-september-project-budget-register.md)
