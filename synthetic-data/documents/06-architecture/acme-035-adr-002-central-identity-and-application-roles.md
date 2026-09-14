---
id: "ACME-035"
title: "ADR-002 Central Identity and Application Roles"
document_type: "architecture_decision"
organization: "Acme Corporation"
owner: "Imani Brooks"
created: "2026-08-06"
updated: "2026-08-06"
department: "IT Operations"
status: "current"
synthetic: true
related_documents: ["ACME-013", "ACME-025", "ACME-047"]
related_people: []
related_systems: []
projects: []
---

# ADR-002 Central Identity and Application Roles

> Synthetic Acme Corporation document. Reference date: September 11, 2026.

## Context
Teams previously treated a product administrator label as permission to change infrastructure. That created unclear access requests and made periodic reviews difficult. Acme needs a common identity source while retaining system-specific least-privilege roles.

## Decision
Use central SSO for employee authentication and require MFA for production sessions. Application roles authorize product behavior; infrastructure roles authorize operational actions. Mapping one to the other implicitly is prohibited. Production elevation follows the ticketed four-hour grant in the access procedure.

## Responsibility split
IT Operations configures identity integrations and infrastructure grants. Engineering enforces application permissions in backend handlers. People Operations coordinates joiner and leaver events; managers approve the business need. A hidden menu item alone does not enforce authorization.

## Consequences and testing
Every privileged backend operation must reject callers lacking the required application role. An expired infrastructure grant must fail even if the user's application administrator role remains active. Tests should cover missing role, expired grant, and revoked account cases. This record specifies the design, not actual authentication wiring in the KnowledgeBridge demonstration application.

## Related documents
- [ACME-013 — Production Access Procedure](../03-policies/acme-013-production-access-procedure.md)
- [ACME-025 — Access Cleanup Closure Report](../04-projects/acme-025-access-cleanup-closure-report.md)
- [ACME-047 — New Employee First Week Checklist](../09-training/acme-047-new-employee-first-week-checklist.md)
