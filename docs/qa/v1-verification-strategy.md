# TinyAdmin V1 Verification Strategy

**Status:** QA draft for Security/Architect/Code Review — not implementation start authority  
**Issue:** [#7](https://github.com/balarajeai/tinyadmin/issues/7)  
**Author:** QA Engineer  
**Mode:** Production-MVP (lean evidence contract for safe V1 start — NOT comprehensive suite)  
**Date:** 2026-09-23

**Governing inputs (established):**
- Product lock: `docs/product/v1-requirements.md`
- Architecture: `docs/architecture/v1-system-architecture.md` (Issue #2)
- Protocol: `docs/architecture/agent-cloud-protocol-v1.md` (Issue #3)
- Domain: `docs/architecture/domain/v1-domain-model.md` (Issue #6)
- Threat model: `docs/security/v1-threat-model.md` (Issue #4)
- Security requirements: `docs/security/v1-security-requirements.md` (Issue #5)

This document defines the MINIMUM verification strategy required to safely start implementing TinyAdmin V1. It answers: **"What evidence must exist before we can trust each critical TinyAdmin capability?"**

---

## 1. Purpose & scope

### 1.1 Purpose

This is a **lean evidence contract** between Backend, Agent, Frontend, QA, Security, and Platform teams during V1 implementation. It establishes:

1. **When** each test level is appropriate (unit vs integration vs API vs E2E)
2. **What** evidence is required for each security-critical invariant
3. **How** to verify without blocking on cosmetic/routine QA
4. **Which** capabilities require negative testing and failure-mode coverage

This is NOT a giant test suite design. This is the strategy that guides what tests to write and what evidence counts as "done."

### 1.2 In scope

- Test level selection criteria (lowest-cost that proves the invariant)
- Security invariant evidence mapping to SR-* requirements from Issue #5
- Database testing approach (PostgreSQL first; MongoDB sequenced)
- First vertical slice verification checklist (Unlock User Safe Action end-to-end)
- Negative testing principles for authorization and tenant isolation
- Minimum failure-mode testing requirements
- Audit verification approach aligned with V1 Audit Event Catalog
- Secrets verification strategy
- FA-CONN-BLAST / SR-CONN-001 evidence mapping
- Production environment UX distinguishability verification (QA-EVIDENCE-ENV-PROD-UX)
- Test data rules and CI expectations
- Evidence states and release-blocking principles

### 1.3 Explicit non-goals

- **NOT** a complete test suite with hundreds of test cases
- **NOT** a full traceability matrix for every future test
- **NOT** a requirement to build MongoDB Agent integration suite before Mongo implementation
- **NOT** a performance/chaos/load/monitoring program for V1
- **NOT** a requirement for full E2E suite on every tiny frontend change
- **NOT** cosmetic/routine QA that can be handled with normal code review
- **NOT** blocking implementation on nice-to-have verification
- **NOT** enterprise certification or compliance frameworks

### 1.4 Operating principle

**PRODUCTION-MVP:** Ship stoppers only. Issues #2/#3/#4/#5/#6 decisions are established. This strategy does NOT reopen architecture, security requirements, or topology debates (e.g., one-Agent-per-database is NOT required; FA-CONN-BLAST residual HIGH is acknowledged with compensating controls).

---

## 2. Test levels — when to use each

The goal: **prefer the lowest-cost test level that proves the invariant**. Higher levels are more expensive and brittle.

### 2.1 UNIT tests

**When:** Pure validation logic, permission helpers, state machines, canonicalization, parameter validation, rollback eligibility checks, redaction rules.

**Examples:**
- Action parameter type validation (string/int/UUID) without DB
- Permission key matching (define≠run) in isolation
- Preview limitation flag computation
- Redaction logic strips secrets from audit payload
- Operation lifecycle state transitions (pending → succeeded/failed/unknown)
- Enrollment token hash generation and expiry checks
- Max affected records boundary validation
- Canonical JSON digest computation (JCS)

**Evidence:** Fast, deterministic, no external dependencies. Run on every commit.

**Do NOT use unit tests for:** Tenant isolation across real orgs, RBAC with real DB membership, idempotency with actual persistence, transactions.

---

### 2.2 INTEGRATION tests (Spring + PostgreSQL)

**When:** Tenant isolation, RBAC enforcement, audit persistence, transactions, outbox behavior, idempotency with durable state, DB constraints, repository queries, multi-step flows that require real PostgreSQL semantics.

**Use Testcontainers** (or equivalent real PostgreSQL) when DB semantics matter: row-level locks, constraints, transaction isolation, affected-row counts, audit append-only enforcement.

**Examples:**
- Cross-org resource access denied with real org_id predicates
- User membership revoke → subsequent API calls denied
- Operation with duplicate operation_id → second execute returns cached result
- Audit row INSERT succeeds but UPDATE/DELETE rejected by DB role/trigger
- Outbox TTL/depth enforcement with real clock and queries
- Connection org/env immutability enforced by DB constraint
- Agent revoke cancels queued outbox commands for that agent_id
- Transaction rollback on validation failure leaves DB unchanged

**Evidence:** Real PostgreSQL behavior, realistic failure modes, prove constraints work. Slower than unit; faster than E2E.

**Do NOT use integration tests for:** Agent network protocol, WSS session, full browser UX flows.

---

### 2.3 AGENT INTEGRATION tests

**When:** Agent↔PostgreSQL/MongoDB connectivity, command verification, connection allowlist, Safe Action execution, preview non-mutation, max affected records enforcement, duplicate operation handling, result recovery, rollback conditions.

**Agent + real customer DB (PostgreSQL first; MongoDB later):**

**Examples:**
- Agent verifies Cloud-signed envelope; tampered payload rejected
- Agent rejects command with mismatched org/env/connection binding
- Preview query leaves DB unchanged (checksum/row-version before/after)
- Execute respects max_affected_records=1 (aborts if >1 match)
- Duplicate operation_id after success → no second mutation
- Agent reconnect → cancel/revoke sync before pending mutate execution
- Connection allowlist: command for non-configured connection_id rejected
- Rollback eligibility: state changed → rollback fails closed
- Result durable until Cloud authenticated ack received

**MongoDB Agent integration:** Deferred until MongoDB implementation sprint. Mark tests as LATER; do NOT block PostgreSQL.

**Evidence:** Proves Agent honors Cloud authorization, enforces limits, handles failures, never invents success.

---

### 2.4 API tests

**When:** Authentication, authorization gates, org/environment boundaries, invitation/enrollment flows, Action lifecycle (preview → confirm → execute), audit APIs, RBAC permission checks.

**Examples:**
- Login with wrong password denied
- Cross-org API request (valid session, wrong org resource ID) denied
- Preview without run permission denied
- Confirm without preview fingerprint rejected
- Execute without valid Confirmation rejected
- Expired envelope rejected at Cloud before dispatch
- Revoked Agent cannot open new session
- Audit read requires audit.read permission
- Production Action shows production confirmation UX flags (QA-EVIDENCE-ENV-PROD-UX)

**Evidence:** Black-box HTTP API contract validation. Proves boundaries work without UI.

**Do NOT use API tests for:** Browser-specific JS validation, CSS rendering, visual confirmation modals.

---

### 2.5 E2E / Playwright tests

**When:** Critical user journeys that cross multiple boundaries and require browser interaction. Reserve for **high-value, high-risk paths only**.

**V1 critical journeys (minimal set):**
1. **Golden path:** Login → Select Org → Navigate to Env (Production) → Enroll Agent → Configure Connection → Trigger Discovery → Search Records → Select Safe Action (Unlock User) → Preview → Confirm (production warning visible) → Execute → Verify Audit
2. **Cross-tenant denial:** Login as Org A user → attempt to access Org B Agent/Connection/Action via URL manipulation → denied without data leakage
3. **Revoke mid-session:** Login → Start Action preview → Admin revokes membership → Confirm/Execute denied

**Do NOT require E2E for:** Every frontend field validation, every button style, every copy change, every minor layout tweak, routine non-security-boundary changes.

**Evidence:** Screen recording or screenshot artifact showing critical path success; CI run log with assertions.

---

## 3. Critical security invariants matrix

This table maps each security-critical invariant to its preferred evidence level and the Issue #5 security requirements it satisfies.

| Invariant | Preferred evidence level | Maps to SR-* | Release blocking? |
| --- | --- | --- | --- |
| **Tenant isolation** | Integration | SR-TENANT-001, SR-TENANT-002 | **YES** |
| **Environment isolation** | Integration + API | SR-ENV-001, SR-ENV-002 | **YES** |
| **Authorization (Define≠Run, Read≠Mutate, separate Rollback)** | Unit + Integration + API | SR-AUTHZ-001, SR-AUTHZ-002, SR-AUTHZ-003 | **YES** |
| **Agent identity authenticated** | Agent Integration + API | SR-AGENT-001, SR-AGENT-002 | **YES** |
| **Command authenticity (signed envelopes)** | Agent Integration | SR-CMD-001 | **YES** |
| **Replay/idempotency** | Agent Integration | SR-CMD-002 | **YES** |
| **Connection binding** | Agent Integration + API | SR-ENV-001, SR-CONN-001 (CC-4/CC-6) | **YES** |
| **Safe Actions (no arbitrary SQL/Mongo)** | Unit + API + Agent Integration | SR-ACTION-001 | **YES** |
| **Approved-field edits (not generic editor)** | API + Agent Integration | SR-ACTION-001 | **YES** |
| **Max affected records enforced** | Agent Integration | SR-ACTION-002 | **YES** |
| **Preview never mutates** | Agent Integration | SR-PREVIEW-001 | **YES** |
| **Confirmation required before mutate** | API + Integration | SR-PREVIEW-002 | **YES** |
| **Action pinning (no TOCTOU)** | Integration + API | SR-ACTION-003 | **YES** |
| **Unknown execution (never fabricate success)** | Agent Integration + Integration | SR-EXEC-001 | **YES** |
| **Audit catalog (V1 events)** | Integration | SR-AUDIT-001 | **YES** |
| **Secrets never in Cloud** | Schema Review + Unit + Integration | SR-SECRETS-001, SR-REDACT-001 | **YES** |
| **Rollback fail-closed (conditional only)** | Agent Integration + API | SR-ROLLBACK-001 | **YES** |
| **Production visually distinguishable** | Manual (QA-EVIDENCE-ENV-PROD-UX) | SR-ENV-002 | **YES** |
| **Discovery/search authorized** | API + Integration | SR-DISCOVERY-001 | **YES** |
| **Revoke cancels pending mutates** | Integration + Agent Integration | SR-AUTHZ-002 | **YES** |

**Test assertion binding:** Tests and evidence MUST assert against the exact SR-* requirement IDs from Issue #5 (e.g., SR-TENANT-001, SR-AUTHZ-002) and, where applicable, the exact V1 Audit Event Catalog event-type strings from SR-AUDIT-001 (e.g., `mutation_succeeded`, `mutation_unknown`, `preview_completed`) rather than paraphrased aliases. This ensures traceability and prevents verification drift from established requirements.

**Principle:** Every security-critical invariant needs at least one automated test at the appropriate level. Happy-path alone is NOT sufficient; negative paths required (see §6).

---

## 4. Database testing

### 4.1 PostgreSQL first

V1 prioritizes PostgreSQL. Prefer **real PostgreSQL (Testcontainers)** for:
- Transactions and rollback
- Affected-row limits (UPDATE returns count)
- Tenant/environment isolation with real `organization_id` predicates
- DB constraints (FK, unique, immutable field triggers)
- Idempotency with durable state
- Audit append-only (DB role/trigger denies UPDATE/DELETE)

**Do NOT mock PostgreSQL** for security-critical tests. Mocks miss:
- Constraint violations
- Transaction isolation
- Affected-row semantics
- Lock behavior
- Trigger enforcement

### 4.2 Canonical QA schema (test data)

Define a small **canonical test schema** for Agent integration and Action verification. This is **test data only**, not the product schema.

**Example canonical schema (illustrative):**

```sql
-- Canonical QA test schema (PostgreSQL)
CREATE TABLE test_users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL, -- 'active' | 'disabled'
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed test data
INSERT INTO test_users (id, email, locked, status, failed_login_count)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'locked@example.com', TRUE, 'active', 0),
    ('00000000-0000-0000-0000-000000000002', 'active@example.com', FALSE, 'active', 0);
```

**Unlock User Safe Action test:**
- Target: `locked=true` → `false`
- Max affected records: 1
- Expected audit: before `locked=true`, after `locked=false`, operation succeeded

**Why canonical schema?**
- Provides stable, realistic test surface
- Allows Action/rollback verification without customer data
- Simplifies Agent integration test setup
- Documents expected Safe Action patterns

### 4.3 MongoDB later

MongoDB Agent integration suite is **OUT OF SCOPE** for initial PostgreSQL delivery. Mark MongoDB tests as `LATER` or `@Disabled` with clear comments. MongoDB verification follows the same principles (preview non-mutation, max affected, idempotency) but uses Mongo drivers and semantics.

Do NOT block PostgreSQL implementation on MongoDB suite.

---

## 5. First vertical slice (primary production-quality verification target)

The first end-to-end verification target proves the core mutation path works correctly and securely. This is the **highest-priority verification effort**.

**Vertical slice:** PostgreSQL → Agent connects → schema discovery → user search → Unlock User Safe Action → preview → confirmation → execute → audit.

### 5.1 Checklist (from Issue #7 task brief)

Evidence that MUST exist before the vertical slice can be considered verified:

| # | Evidence requirement | Test level | Status |
| --- | --- | --- | --- |
| 1 | Agent uses local DB credentials only (never sent to Cloud) | Agent Integration + Schema Review | NOT TESTED |
| 2 | Cloud never receives or stores customer DB passwords | Schema Review + API test | NOT TESTED |
| 3 | Agent bound to exactly one org + one env | API + Integration | NOT TESTED |
| 4 | Connection configured with org/env/agent binding | API + Integration | NOT TESTED |
| 5 | Discovery executed; `test_users` schema cached in Cloud | Agent Integration + Integration/API | NOT TESTED |
| 6 | Search for locked users returns expected records | Agent Integration + API | NOT TESTED |
| 7 | Preview shows locked=true → false transition | Agent Integration | NOT TESTED |
| 8 | Preview does NOT mutate DB (checksum unchanged) | Agent Integration | NOT TESTED |
| 9 | Confirmation binds operation_id + preview fingerprint | API + Integration | NOT TESTED |
| 10 | Execute mutates exactly one record (max_affected_records=1) | Agent Integration | NOT TESTED |
| 11 | Duplicate delivery does NOT cause second mutation | Agent Integration | NOT TESTED |
| 12 | Audit records: actor, org, env, action, operation, before/after, result | Integration | NOT TESTED |
| 13 | Unauthorized user (no run permission) denied | API + Integration | NOT TESTED |
| 14 | Wrong environment (staging Action on prod Connection) denied | API + Integration | NOT TESTED |
| 15 | Wrong Connection (Action not bound to Connection) denied | API + Agent Integration | NOT TESTED |
| 16 | Tampered command (invalid signature/digest) rejected by Agent | Agent Integration | NOT TESTED |

**Success criteria:** All 16 items PASS before the vertical slice is considered verified.

**Evidence artifacts:**
- CI test run log showing assertions pass
- Agent integration test log showing envelope verify + execute + result
- Audit query showing required event with before/after
- Screenshot or recording of E2E golden path (optional; recommended for demo)

---

## 6. Negative testing principle

**RULE:** Every security-critical capability requires negative paths in addition to happy path. Happy-path alone is NOT verification.

### 6.1 Required negative tests (minimum)

| Capability | Negative tests required |
| --- | --- |
| **Tenant isolation** | Valid Org A session → Org B resource ID → denied; multi-org user without selecting Org B → Org B resource denied |
| **Environment isolation** | Staging Action → production Connection → denied; Agent bound to staging → production command → rejected |
| **Authorization** | No permission → API denied; define-only → execute denied; run-only → create Action denied; revoked membership → execute denied |
| **Agent identity** | Expired enrollment token → rejected; reused token → rejected; revoked Agent → session denied |
| **Command authenticity** | Tampered payload → digest mismatch → rejected; expired envelope → rejected; wrong connection_id → rejected; unknown kid → rejected |
| **Idempotency** | Duplicate operation_id after success → no second mutation; duplicate after `unknown` → no blind retry |
| **Preview** | Preview path writes to DB → FAIL (no acceptable pass); preview without read permission → denied |
| **Confirmation** | Execute without Confirmation → denied; Confirmation for operation A used for operation B → denied |
| **Action limits** | Client raises max_affected_records → ignored/capped; >N records matched → aborted |
| **Rollback** | State changed → rollback unsafe → rejected; unauthorized rollback → denied; wrong tenant/env → denied |
| **Audit** | Normal ops user attempts UPDATE audit → denied; secret value in audit body → redacted/absent |
| **Secrets** | Connection API accepts raw password → rejected; logs contain DB credentials → FAIL |

**Verification:** Negative tests MUST exist in CI for every row above before the related feature ships.

---

## 7. Failure-mode testing (minimum)

These are the minimum failure scenarios that must be tested to prove the system behaves safely under adverse conditions.

### 7.1 Agent offline / unavailable

| Scenario | Expected behavior | Test level |
| --- | --- | --- |
| Agent offline before dispatch | Commands queue in outbox; UX shows Agent unhealthy; no silent success | Integration + API |
| Agent disconnects after command dispatch | Agent reconnects → cancel/revoke sync → reconciliation; no silent success | Agent Integration |
| Agent never reconnects (TTL exceeded) | Outbox commands expire; Operations stay pending → unknown if intent dispatched | Integration |

### 7.2 Agent crash windows

| Scenario | Expected behavior | Test level |
| --- | --- | --- |
| Crash before DB mutation | On restart: execution state allows safe retry with valid envelope | Agent Integration |
| Crash after DB commit, before local result persist | Mark `unknown`; reconcile — do NOT assume success | Agent Integration |
| Crash after local result persist, before Cloud ack | Resend durable result on reconnect; Cloud idempotent apply | Agent Integration |

### 7.3 Authorization revoked mid-flight

| Scenario | Expected behavior | Test level |
| --- | --- | --- |
| User membership revoked while Operation pending | Queued commands cancelled; Agent rejects execution; audit shows cancelled | Integration + Agent Integration |
| Action disabled after preview, before execute | Execute denied (Action pin mismatch or status check) | API + Integration |
| Agent revoked while commands queued | Queued commands cancelled; Agent session terminated | Integration + Agent Integration |

### 7.4 Database transaction failures

| Scenario | Expected behavior | Test level |
| --- | --- | --- |
| DB txn rolled back (constraint violation) | Operation marked failed (not unknown); audit records failure; no partial commit | Agent Integration |
| Affected-record limit exceeded | Agent aborts before commit; Operation failed; audit records rejection | Agent Integration |
| DB connection lost mid-mutation | Result `unknown` if commit status indeterminate; honest audit | Agent Integration |

### 7.5 Result path failures

| Scenario | Expected behavior | Test level |
| --- | --- | --- |
| Result lost in transit | Agent retries until authenticated ack; Operation stays pending until result received | Agent Integration + Integration |
| Duplicate result delivery | Cloud idempotent apply by operation_id; no duplicate audit | Integration |
| Unauthenticated result POST | Rejected; Operation stays pending; no silent success | API + Agent Integration |

**Principle:** Never invent success. Indeterminate outcomes stay `unknown` until reconciliation establishes the truth.

---

## 8. Audit verification

Audit verification uses the **V1 Audit Event Catalog** from SR-AUDIT-001 (Issue #5). QA does NOT invent a separate policy.

### 8.1 Required audit events (from catalog)

For V1 vertical slice, the following events MUST be verified:

| Event type | Required fields | WHEN required | Verification method |
| --- | --- | --- | --- |
| `mutation_requested` | actor, org, env, operation_id, action_or_config_id, target_summary, status | Mutate intent begins | Integration test queries audit after preview/confirm; asserts event present |
| `mutation_rejected` | actor, org, env (if known), operation_id (if known), status (reject reason) | Authz deny, env mismatch, confirmation invalid, or pre-mutation reject | Integration test for deny cases; asserts rejection event |
| `preview_completed` | actor, org, env, operation_id, action_or_config_id, target_summary, status | Preview completes (success or failure) | Agent Integration; asserts event after preview |
| `confirmation_completed` | actor, org, env, operation_id, action_or_config_id, status | Confirmation record created | API + Integration; asserts event after confirm |
| `mutation_succeeded` | actor, org, env, operation_id, action_or_config_id, target_summary, status, **before/after (redacted)** | Successful customer-DB mutation | Agent Integration + Integration; asserts event with before/after fields |
| `mutation_failed` | actor, org, env, operation_id, action_or_config_id, target_summary, status | Mutation attempted and definitively failed | Agent Integration; asserts event for txn rollback / reject |
| `mutation_unknown` | actor, org, env, operation_id, action_or_config_id, target_summary, status=unknown | Outcome indeterminate | Agent Integration (crash/timeout scenario); asserts honest unknown status |
| `rollback_requested` | actor, org, env, operation_id, rollback_of_operation_id, target_summary, status | Rollback requested | Integration |
| `rollback_succeeded` | actor, org, env, operation_id, rollback_of_operation_id, status, **before/after (redacted)** | Rollback mutation succeeds | Agent Integration + Integration |
| `rollback_failed` | actor, org, env, operation_id, rollback_of_operation_id, status | Rollback rejected or fails | Integration |
| `agent_enrolled` | actor, org, env, agent_id, status | Agent activation completes | API + Integration |
| `agent_revoked` | actor, org, env, agent_id, status | Agent revoked | API + Integration |

### 8.2 Audit verification rules

**How QA verifies audit:**

1. **Event emitted:** For each REQUIRED event type, trigger the WHEN condition and assert exactly one matching event exists in the audit table/API.
2. **Required fields present:** Assert every REQUIRED FIELD is non-null and non-empty (except fields marked N/A for that event type).
3. **Operation correlation:** Assert `operation_id` matches the triggering Operation.
4. **Before/after rules:** For `mutation_succeeded` and `rollback_succeeded`, assert before/after fields reflect the mutation (after redaction). Before/after MUST NOT include secrets (see §10).
5. **Redaction:** Assert no secret substrings from SR-REDACT-001 appear in audit body (see §10.1).
6. **Normal users cannot modify/delete:** Integration test: app role/API attempts UPDATE/DELETE on audit rows → denied by DB role or trigger.

### 8.3 CR-SR-001 disposition (carry forward from Issue #5)

**Clarification (do NOT reopen Issue #5):**

- `mutation_failed` = definitive known failure where the intended mutation did NOT apply (or applied none).
- `mutation_unknown` = partial, indeterminate, or outcome cannot be proven.

**Prevent:** Classifying uncertain mutations as `mutation_failed` when the outcome is not definitively known. If in doubt → `unknown`.

**QA verification:** Test scenarios where Agent crashes mid-mutation → assert audit shows `mutation_unknown`, NOT `mutation_failed`, until reconciliation proves the outcome.

---

## 9. QA-EVIDENCE-ENV-PROD-UX (production distinguishability)

**Requirement:** SR-ENV-002 states that production operations must be visually and operationally distinguishable from non-production in UX. The automated gate (API test for confirmation requirement) is separate from the visual/UX evidence.

**Evidence item ID:** `QA-EVIDENCE-ENV-PROD-UX`

**Verification method:** Manual checklist with screenshot/recording evidence.

### 9.1 Checklist (manual verification required before production Go-live)

| # | Check | Evidence artifact | Status |
| --- | --- | --- | --- |
| 1 | Environment selector shows **PRODUCTION** label clearly | Screenshot | NOT TESTED |
| 2 | Preview for production Action shows production context label/indicator | Screenshot | NOT TESTED |
| 3 | Confirmation modal/page for production mutation shows **PRODUCTION** warning/indicator | Screenshot | NOT TESTED |
| 4 | Execute result page indicates production context | Screenshot | NOT TESTED |
| 5 | Production operations cannot visually masquerade as staging/non-prod (no ambiguous labels) | Screenshot comparison | NOT TESTED |
| 6 | Confirmation for production requires explicit acknowledgment (not bypassable by client-side skip) | API test (automated; SR-ENV-002) | NOT TESTED |

**Evidence storage:** Screenshots/recordings stored in `docs/qa/evidence/` (or CI artifacts) and linked in the production Go-live PR.

**Blocking:** This is NOT a full design system requirement. This is a SAFETY verification that production is obvious. Any ambiguity that could lead to accidental production mutation is release-blocking.

---

## 10. Secrets verification

**Requirement:** Customer DB credentials and other secrets must never reach TinyAdmin Cloud database, audit, logs, API errors, or browser responses.

### 10.1 Secret classes (from SR-REDACT-001)

The following MUST NOT appear in Cloud:

1. Customer DB credentials (username/password/connection strings with embedded secrets)
2. Raw enrollment tokens (hashed only)
3. Agent private keys (Cloud stores public keys only)
4. Cloud signing private keys (Cloud KMS/secret store only)
5. Session/authorization bearer secrets (except in secure session store)

### 10.2 Verification approach

| Secret class | Verification method | Test level |
| --- | --- | --- |
| Customer DB credentials | Schema review: no password column on Connection; API test: raw credential POST rejected; Agent integration: `customer_secret_ref` is opaque label only | Schema + API + Agent Integration |
| Enrollment tokens | Integration test: DB stores hash only; log scan: no raw token in application logs; API response: token returned once, not in subsequent reads | Integration + Log Scan |
| Agent private keys | Agent local storage: restricted permissions; Cloud stores public key only (schema review) | Schema + Agent Integration |
| Cloud signing keys | Platform: KMS or secure secret store (ops verification); not in Cloud DB or logs | Platform + Schema |
| Session secrets | Secure session store (Redis or DB encrypted); not in logs or responses | Integration + Log Scan |
| Audit/result secrets | Redaction middleware strips secrets from audit before/after; fixture tests assert no raw secrets in audit rows | Integration |
| Outbound-only / no public DB ports | Platform evidence: Agent initiates outbound connection; customer DB need not expose public `5432`/`27017` to TinyAdmin Cloud (aligns with SR-SECRETS-001 architecture/platform split) | Platform + Architecture conformance |

### 10.3 Automated scanning

**Log/audit redaction scan:** Integration test or CI lint step scans logs and audit fixture output for patterns like:

- `password=`, `PASSWORD:`, `pass=`
- `postgres://user:password@host`
- `mongodb://user:password@host`
- `Bearer <token>`
- Private key PEM headers (`-----BEGIN PRIVATE KEY-----`)

**Presence of secret patterns in Cloud logs/audit = FAIL.**

**Evidence:** CI test run log showing scan executed and no secrets found; or failed build if secrets detected.

---

## 11. FA-CONN-BLAST / SR-CONN-001 (compensating controls verification)

**Context:** V1 allows one Agent to manage multiple Connections (established topology). FA-CONN-BLAST residual **HIGH** is acknowledged: Agent-host compromise yields all Connection credentials on that Agent.

**Security requirement:** SR-CONN-001 mandates compensating controls CC-1..CC-7.

### 11.1 CC-1..CC-7 verification matrix

This table uses the **Product vs Ops/Docs matrix** from SR-CONN-001 in Issue #5. Each CC must have objective evidence before Connection multi-binding ships.

| CC | Control | Enforcement | Verification method | Expected evidence | Status |
| --- | --- | --- | --- | --- | --- |
| **CC-1** | Dedicated least-privilege DB user per Connection | **Ops/Docs** (customer responsibility) | Manual checklist: Connection hardening guidance present in docs; sample grant snippet included | `docs/operations/connection-hardening.md` or equivalent merged; checklist item `CC-1-least-privilege-user` signed off | NOT TESTED |
| **CC-2** | Explicit Connection configuration only | **Product** | Integration test: mutation targeting undeclared Connection id denied; Agent has no ad-hoc Connection path | CI test log + assertion pass | NOT TESTED |
| **CC-3** | Per-Connection secret isolation where practical | **Ops/Docs** + Product | Manual checklist: guidance recommends distinct secret refs per Connection; Cloud API/schema test: only opaque `customer_secret_ref` stored, never raw secrets | Hardening doc + schema/API test | NOT TESTED |
| **CC-4** | Agent Connection allowlist matches Cloud Connection IDs | **Product** | Agent integration test: command with Connection id outside allowlist rejected | CI Agent test log | NOT TESTED |
| **CC-5** | Agent immutable org/environment binding | **Product** | API/DB test: post-activation Agent rebind rejected; command with mismatched org/env rejected | CI test (aligns with SR-AGENT-003 / SR-ENV-001) | NOT TESTED |
| **CC-6** | Command→Connection binding in signed envelopes; mismatch reject | **Product** | Tamper test: valid envelope rewritten with different `connection_id` fails Agent verify | CI Cloud+Agent test (aligns with SR-CMD-001) | NOT TESTED |
| **CC-7** | Guidance for stronger isolation (separate Agent/host) | **Ops/Docs** | Manual checklist: docs explicitly advise separate Agent when reduced blast radius required; states one-Agent-many-Connections remains supported | Docs section present; checklist item `CC-7-stronger-isolation-guidance` signed off | NOT TESTED |

**Topology regression check:** Automated or review assertion that product does NOT require exactly one Connection per Agent to function. One Agent managing multiple Connections must work.

**Evidence:** All CC rows PASS before Connection multi-binding feature ships.

---

## 12. Test data rules

### 12.1 Data sources

- **Synthetic data only:** All test users, orgs, Actions, Connection configs are generated or fixture data.
- **Isolated test DBs:** Use disposable Docker/Testcontainer PostgreSQL; never point tests at production or real customer databases.
- **No real customer data:** Never use real customer production data in tests or fixtures.
- **No real secrets:** Never commit real DB passwords, API keys, enrollment tokens, or private keys to test fixtures or code.

### 12.2 Fixture best practices

- Use UUIDs like `00000000-0000-0000-0000-000000000001` for test org/user/agent IDs (obvious test data).
- Use `example.com` email domains.
- Use `test_` prefix for canonical QA schema objects (e.g., `test_users` table).
- Use `localhost` or `testcontainer.local` for test DB hosts.
- Redact any real-looking secrets before committing fixtures.

### 12.3 Data cleanup

- Integration tests: clean up test orgs/users/operations after each test or use transactional rollback.
- Agent integration tests: truncate canonical QA schema tables between tests.
- Do NOT leave test pollution that affects subsequent test runs.

---

## 13. CI expectations (lean)

**Goal:** Prove the change works without requiring the entire future test suite on every PR.

### 13.1 Implementation PRs (general)

**Minimum CI requirements:**
- Compile/build succeeds
- Unit tests pass
- Relevant integration tests pass (only for touched modules)
- Lint/static analysis where configured (no new violations)

**Do NOT require:**
- Entire E2E suite for backend-only change
- Full Agent integration suite for unrelated frontend change
- Performance/load tests on every PR
- Manual QA sign-off before CI merge (unless security-sensitive)

### 13.2 Security-sensitive PRs (additional requirements)

PRs that touch authentication, authorization, tenant isolation, Agent command verification, mutation safety, audit, secret handling, or rollback MUST additionally run:

- Tenant isolation cross-org deny tests
- Authorization define≠run / permission matrix tests
- Agent command verification tests (signature, binding, idempotency)
- Preview non-mutation tests
- Audit append-only enforcement tests
- Secret redaction/absence tests

**Evidence:** CI job log shows these tests executed and passed.

### 13.3 NOT required for every frontend change

- Routine styling, copy, layout changes without security boundaries: code review sufficient; no mandatory E2E gate.
- Minor bug fixes with existing test coverage: run affected tests only.
- Documentation updates: no test execution required (lint/spell-check optional).

**Exception:** If the frontend change affects confirmation UX, production warnings, authorization flows, or security boundaries → security-sensitive CI requirements apply.

---

## 14. Evidence states

Every verification item in this strategy uses one of these states:

| State | Meaning |
| --- | --- |
| **NOT TESTED** | No test exists or no evidence of execution |
| **PASS** | Test exists, executed, and passed; evidence artifact available (CI log, screenshot, recording, report) |
| **FAIL** | Test executed and failed; blocker for the gated feature |
| **BLOCKED** | Test cannot run due to missing dependency or environment |

**"Looks correct" is NOT evidence.** PASS requires:
- Automated test: CI job log with assertion pass
- Manual test: Screenshot, recording, or structured checklist with sign-off
- Schema review: Review comment or document confirming check completed
- Log scan: CI artifact or report showing scan executed and passed

**Agent claims / implementer claims are NOT evidence.** Evidence must be reproducible: another engineer can re-run the test or view the artifact and verify the claim.

---

## 15. Release-blocking principle

### 15.1 What blocks V1 release?

Failures that could cause:

1. **Cross-tenant access** (Org A reads/mutates Org B data)
2. **Wrong-environment execution** (staging Action on production DB)
3. **Unauthorized mutation** (no permission, revoked user, expired authorization)
4. **Arbitrary DB mutation** (SQL/Mongo console bypassing Safe Actions)
5. **Duplicate mutation** (replay/idempotency failure)
6. **Customer-data corruption** (preview mutates, max-record bypass, unsafe rollback)
7. **Secret exposure** (DB credentials in Cloud, logs, audit, API errors)
8. **False success** (Cloud invents success without Agent confirmation)
9. **Missing required audit** (mutation without audit trail)
10. **Unsafe rollback** (rollback when state changed, or unconditional restore)

**Severity:** Any test failure in the above categories is **release-blocking** for the related feature. Must be fixed or explicitly Founder-accepted before Go-live.

### 15.2 What does NOT block release?

- Routine visual/copy issues (typos, button alignment, non-critical UX polish)
- Performance optimization (unless causing timeouts that break functionality)
- Nice-to-have features not in V1 scope (billing, SSO, AI)
- Non-security test flake (retry once; investigate but do not block if rare)
- Cosmetic lint warnings (fix or suppress; do not block merge)

**Judgment call:** Chief of Staff / QA / Security / Architect together decide if an issue is release-blocking. Default: security-critical = blocking; cosmetic = non-blocking.

---

## 16. Later work (out of scope for V1 start)

These are explicitly deferred. They may be useful later but are NOT required to start V1 implementation safely:

- **MongoDB Agent integration test suite** — deferred until MongoDB implementation sprint
- **Broader E2E coverage** — add incrementally; not required for every feature before merge
- **Load/performance testing** — valuable for production readiness; not required for V1 implementation start
- **Chaos testing** (Agent random disconnect, DB flake) — useful for resilience; not V1 MVP
- **Full E2E suite on every PR** — too expensive; reserve for critical paths and security-sensitive changes
- **Traceability matrix** — may be useful for audit/compliance later; not required for V1 implementation
- **Automated penetration testing** — consider post-V1 or during hardening phase

**Do NOT block V1 implementation on these items.**

---

## 17. Explicit statements

### 17.1 Agent claims / implementer claims are NOT evidence

**Statement:** "The Agent verifies the signature" or "I checked and it works" is NOT evidence.

**Evidence requires:** Automated test showing tampered signature rejected, OR Agent integration test log with assertion pass, OR reproducible command showing verification.

### 17.2 Green CI alone is NOT done

**Statement:** Passing CI is necessary but NOT sufficient for "done."

**Definition of Done (from engineering DEFINITION_OF_DONE):**
1. Acceptance criteria met
2. Tests written AND executed (not just planned)
3. Evidence artifacts exist (CI log, screenshot, review comment)
4. No secrets committed
5. Security implications considered (for security-sensitive changes)
6. Rollback/recovery considered (for production changes)

Green CI proves (1) and (2) partially. Full "done" requires evidence artifacts and reviews where applicable.

### 17.3 Maps gates to risk for upcoming implementation

**Implementation priority based on risk:**

| Risk level | Verification priority | Examples |
| --- | --- | --- |
| **CRITICAL/HIGH security** | Negative + failure-mode evidence required before merge | Tenant isolation, authorization, command authenticity, secrets, audit |
| **MEDIUM security** | Happy path + basic negative test before merge | Discovery authz, session timeout, preview limitation flags |
| **LOW risk** | Unit/integration coverage; review sufficient | Parameter validation, display logic, copy changes |

Security-sensitive PRs (see §13.2) require more comprehensive evidence than routine changes.

### 17.4 Engineering DEFINITION_OF_DONE alignment

This verification strategy aligns with `DEFINITION_OF_DONE.md` from the engineering governance repo. Key points:

- Tests must be executed, not just written (CI log proves execution)
- Evidence artifacts required (not "looks good to me")
- Security implications considered for security-sensitive changes
- Rollback/recovery considered for production-impacting changes
- No secrets committed (fixture scan / review)

QA will reference DEFINITION_OF_DONE in PR reviews for security-sensitive and high-risk changes.

---

## Document history

| Date | Change |
| --- | --- |
| 2026-09-23 | Initial V1 verification strategy (Issue #7) |
