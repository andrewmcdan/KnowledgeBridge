---
id: "ACME-027"
title: "Aurora Discovery Workshop"
document_type: "workshop_notes"
organization: "Acme Corporation"
owner: "Leo Martin"
created: "2026-08-26"
updated: "2026-08-26"
department: "Engineering"
status: "incomplete"
synthetic: true
related_documents: ["ACME-020", "ACME-032", "ACME-038", "ACME-044"]
related_people: ["Priya Shah", "Leo Martin", "Jordan Blake", "Samira Cole"]
related_systems: ["Beacon"]
projects: ["Aurora"]
---

# Aurora Discovery Workshop

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Workshop context
Date: August 26, 2026. Participants: Leo Martin, Priya Shah, Jordan Blake, and Samira Cole. The group reviewed regional reporting needs for Beacon and the possibility of a European pilot. No customer contract or production region change was approved.

## Findings
Customer Success needs reports grouped by customer-assigned region tags. Engineering can add the grouping without assuming a physical storage region. Jordan noted that a report label such as 'Europe' is not evidence that the underlying data is hosted in Europe.

## Deferred decisions
The hosting region, cross-region recovery arrangement, and treatment of existing records remain open. Leo will prepare a trade-off review; Jordan will review operational readiness. The team did not select a cloud region during the workshop and did not authorize copying production data into a new region for experimentation.

## Next steps
Confirm the pilot's actual customer requirement, resolve the retention discrepancy, and document any proposed migration as an architecture decision. October 6 remains a planning target conditional on those gates. Questions asking for an approved European hosting region should receive an explicit knowledge gap, not the region most often mentioned during discussion.

## Related documents
- [ACME-020 — Project Aurora Reporting Plan](../04-projects/acme-020-project-aurora-reporting-plan.md)
- [ACME-032 — Security Retention Review August 29](acme-032-security-retention-review-august-29.md)
- [ACME-038 — ADR-005 Beacon Regional Storage Decision Pending](../06-architecture/acme-038-adr-005-beacon-regional-storage-decision-pending.md)
- [ACME-044 — Beacon Administration Guide Draft](../08-products/acme-044-beacon-administration-guide-draft.md)
