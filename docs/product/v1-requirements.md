# TinyAdmin V1 Product Requirements

**Status:** Founder-approved  
**Authority:** Founder / Product Owner  
**Recorded:** 2026-09-19  
**Source of record:** [Issue #1](https://github.com/balarajeai/tinyadmin/issues/1)  
**Governance:** [balarajeai/engineering](https://github.com/balarajeai/engineering)

This document locks **product** scope for TinyAdmin V1. It does **not** decide architecture ADRs, threat-model controls, or implementation design. Those remain with Architect and Security Engineer.

---

## 1. Product thesis

TinyAdmin helps software companies stop giving support and operations staff direct database access.

A company connects PostgreSQL or MongoDB to TinyAdmin and defines safe operations that authorized team members can perform without SQL, Mongo queries, or direct database credentials.

**Core promise:** Stop giving people database access. Support and operations users interact with safe business operations, not raw database capabilities.

---

## 2. ICP / first buyer

**Primary target:** SaaS companies with approximately **2–30 engineers** where developers currently perform small production database operations on behalf of support, operations, or customer-success teams.

**Primary operational users:**

- Support
- Operations
- Customer Success
- Authorized internal administrators

**Core problem:** These users need controlled production-data operations without receiving direct database credentials or writing SQL/MongoDB queries.

---

## 3. V1 principles

1. Support and operations users must not need direct database credentials.
2. Users must not execute arbitrary SQL or arbitrary MongoDB mutations.
3. Mutations are represented as controlled **Actions** (and explicitly configured approved-field edits — see §13).
4. Actions must enforce authorization and tenant isolation.
5. Mutations should support **preview** before execution where technically appropriate.
6. Every mutation must be **auditable**.
7. Safe **rollback** is supported only where it can be honestly guaranteed.
8. Customer database credentials should remain inside customer infrastructure.
9. A TinyAdmin **Agent** in customer infrastructure connects to PostgreSQL or MongoDB and communicates **outbound** to TinyAdmin Cloud.
10. Customer databases should not need public exposure of PostgreSQL/MongoDB ports for TinyAdmin.
11. TinyAdmin Cloud should start as a **modular monolith** unless Architect documents a concrete reason otherwise.
12. V1 supports PostgreSQL and MongoDB (sequenced — see §7).
13. V1 excludes arbitrary SQL, internal-tool UI builders, dashboard/workflow builders, charts, and AI-generated operations.
14. AI capabilities may be considered later; they are **not** part of V1.

---

## 4. Authentication (V1)

**In scope:**

- Email/password authentication
- Email invitations
- Secure password reset
- Session management

**Out of initial V1:** Enterprise SSO/SAML.

**Constraint:** Architecture must not prevent future OIDC/SAML support.

---

## 5. Tenancy

- TinyAdmin is **multi-tenant**.
- A customer is an **Organization**.
- A user **MAY** belong to multiple organizations.
- Every tenant-owned resource must have explicit organization ownership and tenant isolation.
- Cross-tenant access is a **security-critical failure**.

---

## 6. Environments

Production and staging are **not** merely cosmetic labels.

TinyAdmin must maintain **meaningful isolation** between environments:

- Connections
- Agents
- Credentials/configuration
- Action execution context

…must belong to a specific environment.

Exact technical isolation model: **Architect + Security Engineer**.

Production operations must be visually and operationally distinguishable from non-production.

---

## 7. Database support

- **PostgreSQL** is the first implementation priority.
- **MongoDB** remains part of V1 but must not block initial PostgreSQL development.
- Architecture must support both without forcing an artificial common query model.
- Prove PostgreSQL first; deliver MongoDB within the V1 product milestone.

---

## 8. Preview semantics

- Where technically safe, Action preview uses **current data** from the customer database via the TinyAdmin Agent.
- Preview **must not** mutate the database.
- Preview must show what operation will occur and which records are expected to be affected.
- Where an exact live preview cannot safely guarantee the final result, TinyAdmin must communicate that limitation clearly (no false authority).

Detailed preview protocol: **Architect + Security**.

---

## 9. Rollback

- TinyAdmin will **not** promise universal rollback.
- Rollback is available only when TinyAdmin can determine the operation can be safely reversed.
- Rollback must verify that relevant state has not changed in a way that makes reversal unsafe.
- Where safe rollback cannot be guaranteed, do **not** present rollback as available.
- Every rollback attempt must be authorized and audited.

Exact rollback safety model: **Architect + Security**.

---

## 10. Database credentials (HARD V1 REQUIREMENT)

- Customer database passwords and credentials **must NOT** be stored in TinyAdmin Cloud.
- Credentials remain inside customer-controlled infrastructure and are available only to the TinyAdmin Agent or an approved local secret mechanism.
- The TinyAdmin Agent initiates **outbound** encrypted communication to TinyAdmin Cloud.
- Customers should not need to expose PostgreSQL port 5432, MongoDB port 27017, or equivalents publicly for TinyAdmin.
- Any proposed exception requires explicit **Founder and Security** approval.

---

## 11. Agent topology

**Default V1 model:** One TinyAdmin Agent per customer environment/network may manage multiple explicitly configured database connections.

- Not required: one Agent process per database.
- Connections must still be individually identified, authorized, configured, and audited.
- Architect may recommend refinements if required for security or isolation.

---

## 12. Audit retention

- Default V1 minimum retention: **1 year**.
- Audit records should establish at least: actor, organization, environment, Action, target, timestamp, operation ID, before/after state where appropriate, result/status, rollback relationship where applicable.
- Normal operational users must not modify or delete audit history.
- Integrity controls and export/archive strategy: **Security / Architect**.

---

## 13. Field editing vs Actions

V1 supports **both**:

- Controlled **approved-field editing**
- Predefined **Safe Actions**

Constraints:

- Actions are preferred for sensitive or business-significant mutations.
- Approved-field editing must be explicitly configured.
- Must never become an unrestricted generic database editor.
- Users cannot arbitrarily select and mutate fields merely because those fields exist.
- Authorization and audit apply to both mechanisms.

---

## 14. Billing / seats

**Out of scope for initial V1:** billing, subscriptions, payment processing, seat-based monetization.

Architecture should avoid obvious blockers to adding billing later. No billing system in initial V1 development.

---

## 15. V1 capability baseline (in scope)

- Authentication (as §4)
- Organizations / tenants
- Users and invitations
- Roles and permissions
- Database connection registration
- TinyAdmin Agent registration
- PostgreSQL support (priority)
- MongoDB support (within V1, sequenced)
- Schema/collection discovery
- Record search/filter/view
- Approved-field editing (configured, not generic)
- Safe Actions
- Action preview
- Action confirmation
- Audit trail
- Simple safe rollback (only when honest/safe)
- Production/staging environment isolation (meaningful, not cosmetic)

---

## 16. V1 non-goals (explicit)

TinyAdmin V1 must **not** become or include:

- Arbitrary SQL execution
- Mongo shell / arbitrary Mongo mutation console
- Generic database administration application
- Internal-tool UI builder
- Dashboard builder
- Workflow builder
- BI / charting platform
- AI-generated database mutation system
- Production infrastructure deployment (not in this product-lock phase)
- Billing / payments / seat monetization

---

## 17. Example Safe Actions (illustrative, not a committed catalog)

Unlock Employee; Activate Customer; Disable User; Reset Failed Login Count; Change Order Status; Approve Transaction; Correct Bank Code; Update Approver; Retry Failed Payment; Mark Invoice Paid; Restore Subscription.

Final Action catalog is product/implementation work after architecture and security gates.

---

## 18. Technology direction (product intent; Architect confirms)

| Layer | Direction |
| --- | --- |
| Frontend | React / Next.js, TypeScript |
| Cloud backend | Java 21+, Spring Boot, PostgreSQL; Redis only where justified |
| Customer Agent | Technology determined by architecture requirements |
| Deployment target | Contabo eventually; **production infrastructure is not being created yet** |
| Cloud shape | Modular monolith initially |

---

## 19. What this document does not decide

Reserved for Architect and/or Security Engineer (see pre-impl issues #2–#7):

- Module boundaries and ADRs
- Agent↔Cloud protocol details
- Exact environment isolation mechanism
- Preview/rollback protocols
- Threat model and control requirements
- Domain model and schemas
- QA evidence standards detail
- Any production deployment design

---

## 20. Definition of ready implication

Implementation epics and Sprint 1 must **not** start until pre-implementation Definition of Ready ([Issue #8](https://github.com/balarajeai/tinyadmin/issues/8)) is green, including Architect and Security deliverables that consume this lock.
