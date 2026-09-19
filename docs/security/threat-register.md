# TinyAdmin V1 Threat Register

**Status:** Formal V1 (Issue #4)  
**Companion narrative:** [v1-threat-model.md](./v1-threat-model.md)  
**Date:** 2026-09-19 (America/Chicago)  
**Owner:** Security Engineer

Actionable register. Severity: `CRITICAL` | `HIGH` | `MEDIUM` | `LOW`. Every CRITICAL/HIGH has a **REQUIRED SECURITY CONTROL** and/or Founder **FA-*** placeholder. Items depending on Agent↔Cloud wire encoding are marked **PENDING(#3)** while still listing required properties (ADR 0006 / architecture §2.4).

Field schema (exact): THREAT ID, ASSET, TRUST BOUNDARY, THREAT, ATTACK / FAILURE SCENARIO, IMPACT, EXISTING ARCHITECTURE CONTROL, REQUIRED SECURITY CONTROL, SEVERITY, OWNER, VERIFICATION METHOD, RESIDUAL RISK.

---

## TM-TEN-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-TEN-001` |
| ASSET | A15 Organization resources; A2 customer data; A7 Actions; A12 audit; A14 connections |
| TRUST BOUNDARY | B2 Cloud tenant isolation |
| THREAT | BOLA/IDOR — access tenant-owned resource by ID without membership check |
| ATTACK / FAILURE SCENARIO | Authenticated Org A user supplies Org B UUIDs for connections, Actions, Agents, audit events, or search results; API returns or mutates cross-tenant data. |
| IMPACT | Cross-tenant read/mutate — CRITICAL product failure; data breach; regulatory exposure. |
| EXISTING ARCHITECTURE CONTROL | Architecture §6.1 #1, §8 AC: tenant filters server-side; client-provided organization id never authorization (SEC-PR10-007). |
| REQUIRED SECURITY CONTROL | SR-TEN-01..03: derive org from trusted session membership; deny-by-default; automated cross-org deny tests on every tenant-owned resource type. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Automated IDOR suite: every tenant resource API with foreign org IDs → 403/404; Code Review + Security on first introduction. |
| RESIDUAL RISK | None if controls hold; residual = CRITICAL defect. |

## TM-TEN-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-TEN-002` |
| ASSET | A15; session context |
| TRUST BOUNDARY | B2 |
| THREAT | Client-supplied organizationId trusted as authorization |
| ATTACK / FAILURE SCENARIO | API accepts organizationId from body/query/header and uses it as authz context instead of server session active-org membership. |
| IMPACT | Trivial cross-tenant privilege; CRITICAL. |
| EXISTING ARCHITECTURE CONTROL | §6.1 #1, §8 SEC-PR10-007 ban. |
| REQUIRED SECURITY CONTROL | SR-TEN-02: never trust client tenant id; active-org from server session only. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Static review + tests attempting client org override. |
| RESIDUAL RISK | None if enforced. |

## TM-TEN-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-TEN-003` |
| ASSET | A2; A13 discovery; A12 audit exports |
| TRUST BOUNDARY | B2 |
| THREAT | Cross-tenant leakage via search, discovery cache, or audit export |
| ATTACK / FAILURE SCENARIO | Shared caches or export jobs omit org filter; user downloads another org's audit or schema metadata. |
| IMPACT | CRITICAL confidentiality breach. |
| EXISTING ARCHITECTURE CONTROL | §2.1 Cloud owns tenant-scoped discovery/audit; §8 tenant filters. |
| REQUIRED SECURITY CONTROL | SR-TEN-*, SR-AUDIT-READ: all reads/exports constrained by trusted org; no shared unscoped caches. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Export/search/discovery cross-org deny tests. |
| RESIDUAL RISK | None if enforced. |

## TM-TEN-004

| Field | Value |
| --- | --- |
| THREAT ID | `TM-TEN-004` |
| ASSET | A4 sessions; A15 multi-org membership |
| TRUST BOUNDARY | B1/B2 |
| THREAT | Multi-org user acts in wrong org context |
| ATTACK / FAILURE SCENARIO | User belongs to Org A and Org B; sticky/incorrect active-org causes Actions against unintended tenant. |
| IMPACT | HIGH — wrong-tenant mutation; may appear as insider error or attacker confusion. |
| EXISTING ARCHITECTURE CONTROL | Product: user MAY belong to multiple orgs; architecture requires server session/org context. |
| REQUIRED SECURITY CONTROL | SR-TEN-04: explicit active-org binding on every request; UI must show active org; switch audited. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Switch-org tests; Action execute asserts org on binding. |
| RESIDUAL RISK | LOW UX mis-click — mitigated by prod distinction + confirm. |

## TM-ENV-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ENV-001` |
| ASSET | A6 Agent binding; A14 connections; A7 Actions |
| TRUST BOUNDARY | B3 Production ↔ staging |
| THREAT | Staging/prod crossover — command or UI targets wrong environment |
| ATTACK / FAILURE SCENARIO | UI shows staging but Cloud dispatches to production connection/Agent; or shared Agent serves both envs. |
| IMPACT | CRITICAL incorrect production mutation. |
| EXISTING ARCHITECTURE CONTROL | §3.4 normative env isolation; Agent exactly one org+one env; immutable connection org/env; Agent rejects mismatch; prod visually/operationally distinguishable (CR-PR10-005). |
| REQUIRED SECURITY CONTROL | SR-ENV-01..03: every sensitive command carries Cloud-bound env; Agent reject; no silent re-label of connection env. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Staging Action cannot bind/execute on prod connection; Agent reject tests; UX evidence. |
| RESIDUAL RISK | None if §3.4 held. |

## TM-ENV-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ENV-002` |
| ASSET | A6; A14 |
| TRUST BOUNDARY | B3/B6 |
| THREAT | Connection or Agent retargeted across environments after create |
| ATTACK / FAILURE SCENARIO | Admin updates environment_id on connection or rebinds Agent to another env without create-new/retire-old. |
| IMPACT | HIGH–CRITICAL silent cross. |
| EXISTING ARCHITECTURE CONTROL | §3.4 #2 immutable org/env on connection; Agent binding at activation. |
| REQUIRED SECURITY CONTROL | SR-ENV-03: immutability enforced in domain/API; changes require new resources. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | API rejects env mutation on connection/Agent. |
| RESIDUAL RISK | None. |

## TM-AUTH-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AUTH-001` |
| ASSET | A3 user credentials |
| TRUST BOUNDARY | B1 |
| THREAT | Credential stuffing / weak password / insecure password storage |
| ATTACK / FAILURE SCENARIO | Attacker brute-forces login; or passwords stored reversibly. |
| IMPACT | HIGH account takeover → Action execute as victim. |
| EXISTING ARCHITECTURE CONTROL | Product §4 email/password in scope; architecture identity module. |
| REQUIRED SECURITY CONTROL | SR-AUTH-*: modern password hashing; password policy; login rate limit; lockout/backoff. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Hash algorithm review; rate-limit tests. |
| RESIDUAL RISK | Stolen password via phishing — MEDIUM residual. |

## TM-AUTH-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AUTH-002` |
| ASSET | A4 sessions |
| TRUST BOUNDARY | B1 |
| THREAT | Session fixation, theft, missing rotation/revocation |
| ATTACK / FAILURE SCENARIO | Stolen cookie used until natural expiry; no rotation on privilege change; no logout-all. |
| IMPACT | HIGH — AT1 capabilities. |
| EXISTING ARCHITECTURE CONTROL | ADR 0003 sessions in PostgreSQL. |
| REQUIRED SECURITY CONTROL | SR-SESS-*: secure cookie flags; regenerate on login; revoke on password change; idle/absolute timeouts. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Session fixation tests; revoke tests. |
| RESIDUAL RISK | XSS-assisted theft mitigated by TM-UI-001. |

## TM-AUTH-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AUTH-003` |
| ASSET | A3 invite/reset tokens |
| TRUST BOUNDARY | B1 |
| THREAT | Invite or password-reset token theft/reuse |
| ATTACK / FAILURE SCENARIO | Long-lived or multi-use email link; token in referrer logs. |
| IMPACT | HIGH account takeover / unauthorized membership. |
| EXISTING ARCHITECTURE CONTROL | Product invites + secure password reset in scope. |
| REQUIRED SECURITY CONTROL | SR-INV-*, SR-RESET-*: single-use, short TTL, bound to email/account; HTTPS-only links. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Reuse after accept fails; expired fails. |
| RESIDUAL RISK | Email compromise R2 MEDIUM. |

## TM-UI-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-UI-001` |
| ASSET | A4 sessions; A2 data in UI |
| TRUST BOUNDARY | B1 |
| THREAT | XSS leading to session exfil or Action forge in browser |
| ATTACK / FAILURE SCENARIO | Unencoded customer field values or Action names executed as script in Cloud UI. |
| IMPACT | HIGH session theft / forced Actions. |
| EXISTING ARCHITECTURE CONTROL | Architecture notes CSRF/XSS as B1 concerns (§6.1). |
| REQUIRED SECURITY CONTROL | SR-UI-XSS: output encoding; CSP baseline; sanitize untrusted record fields in UI. |
| SEVERITY | **HIGH** |
| OWNER | Frontend |
| VERIFICATION METHOD | XSS payload in record/Action name does not execute; CSP headers. |
| RESIDUAL RISK | LOW if encoding+CSP hold. |

## TM-UI-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-UI-002` |
| ASSET | A7 Actions; mutating APIs |
| TRUST BOUNDARY | B1 |
| THREAT | CSRF on state-changing cookie-authenticated APIs |
| ATTACK / FAILURE SCENARIO | Malicious site triggers execute/confirm/invite while user logged into TinyAdmin. |
| IMPACT | HIGH unauthorized mutation. |
| EXISTING ARCHITECTURE CONTROL | §6.1 lists CSRF. |
| REQUIRED SECURITY CONTROL | SR-AUTH-CSRF: SameSite cookies + anti-CSRF token or equivalent for cookie sessions. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Cross-site POST without CSRF token rejected. |
| RESIDUAL RISK | None if enforced. |

## TM-RBAC-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-RBAC-001` |
| ASSET | A8 RBAC; A7 Action defs |
| TRUST BOUNDARY | B9 |
| THREAT | Privilege escalation — define authority conflated with run / view |
| ATTACK / FAILURE SCENARIO | User who can view records or edit schema metadata can define Safe Actions or approved-field allowlists; or runner can redefine Actions. |
| IMPACT | HIGH — expand mutation surface to near-generic editor. |
| EXISTING ARCHITECTURE CONTROL | §3.6 define-vs-run (SEC-PR10-009); §8 AC. |
| REQUIRED SECURITY CONTROL | SR-RBAC-ACT-*: separate define/configure vs run/execute permissions; server-side on every call. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Runner cannot PATCH Action definition; viewer cannot execute. |
| RESIDUAL RISK | None. |

## TM-RBAC-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-RBAC-002` |
| ASSET | A8; mutating operations |
| TRUST BOUNDARY | B1/B9 |
| THREAT | Broken authz — UI hiding treated as authorization |
| ATTACK / FAILURE SCENARIO | API executes Action without server RBAC check because button hidden in UI. |
| IMPACT | HIGH–CRITICAL unauthorized mutation. |
| EXISTING ARCHITECTURE CONTROL | §2.1 server-side RBAC; Security Principles. |
| REQUIRED SECURITY CONTROL | SR-RBAC-*: enforce on every sensitive API; deny by default. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Direct API call without permission → deny. |
| RESIDUAL RISK | None. |

## TM-RBAC-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-RBAC-003` |
| ASSET | A7 approved-field configs |
| TRUST BOUNDARY | B9/B10 |
| THREAT | Approved-field editing becomes unrestricted generic DB editor |
| ATTACK / FAILURE SCENARIO | Config allows any field because it exists in schema; or users select arbitrary columns at runtime. |
| IMPACT | HIGH — defeats Safe Action promise; mass data damage. |
| EXISTING ARCHITECTURE CONTROL | Product §13; architecture §3.6 allowlist only. |
| REQUIRED SECURITY CONTROL | SR-FIELD-*: explicit allowlist per connection/env; runtime reject non-allowlisted fields. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Mutate non-allowlisted field → reject. |
| RESIDUAL RISK | None. |

## TM-AG-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-001` |
| ASSET | A5 Agent credentials; A6 identity |
| TRUST BOUNDARY | B4/B5 |
| THREAT | Agent impersonation via stolen long-lived Agent credential |
| ATTACK / FAILURE SCENARIO | Attacker extracts Agent credential from host or backup; connects as that agent_id; receives commands or exfils results. |
| IMPACT | CRITICAL — full data-plane access for that org/env. |
| EXISTING ARCHITECTURE CONTROL | §2.4 P-AGENT-ID; enrollment/activation §5.1; revoke capability noted in §7 blast controls. |
| REQUIRED SECURITY CONTROL | SR-AGENT-AUTH-*: unique Agent identity; rotation; immediate revocation; detect duplicate concurrent identity where feasible. Mechanism PENDING(#3). |
| SEVERITY | **CRITICAL** |
| OWNER | Architect |
| VERIFICATION METHOD | Revoked Agent cannot fetch/execute; #3 conformance table P-AGENT-ID. |
| RESIDUAL RISK | Host compromise window until revoke — see FA-CONN-BLAST. |

## TM-AG-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-002` |
| ASSET | A10 authz bindings; commands |
| TRUST BOUNDARY | B4/B10 |
| THREAT | Forged or tampered Agent commands |
| ATTACK / FAILURE SCENARIO | Attacker on path or with queue write access injects execute command without valid authenticity/integrity. |
| IMPACT | CRITICAL unauthorized mutation. |
| EXISTING ARCHITECTURE CONTROL | §2.4 P-CMD-AUTH; Agent reject duty §2.2. |
| REQUIRED SECURITY CONTROL | SR-CMD-AUTH / PENDING(#3): Agent detects forgery/tampering; reject. |
| SEVERITY | **CRITICAL** |
| OWNER | Architect |
| VERIFICATION METHOD | #3 PoC: forged command rejected by Agent. |
| RESIDUAL RISK | None if P-CMD-AUTH held. |

## TM-AG-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-003` |
| ASSET | A10; A11 operation results |
| TRUST BOUNDARY | B4/B10 |
| THREAT | Replayed or expired mutating commands |
| ATTACK / FAILURE SCENARIO | Prior execute/rollback replayed after success or after expiry; causes duplicate or stale mutation. |
| IMPACT | CRITICAL duplicate/stale mutation. |
| EXISTING ARCHITECTURE CONTROL | §2.4 P-REPLAY; §3.5 expiry; §5.6.1 idempotent duplicate results. |
| REQUIRED SECURITY CONTROL | SR-REPLAY-*, SR-IDEM-*: reject expired; replay-safe or idempotent per operation_id. PENDING(#3) mechanism. |
| SEVERITY | **CRITICAL** |
| OWNER | Architect |
| VERIFICATION METHOD | Replay execute same binding → reject or no second mutation. |
| RESIDUAL RISK | None if held. |

## TM-AG-004

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-004` |
| ASSET | A10; Agent as deputy |
| TRUST BOUNDARY | B10 |
| THREAT | Confused deputy — Agent executes mutation without valid Cloud-issued authorization binding |
| ATTACK / FAILURE SCENARIO | Cloud RBAC passed historically but Agent receives command lacking/ mismatched binding (actor, org, env, agent, connection, Action, expiry, operation_id). |
| IMPACT | CRITICAL unauthorized mutation. |
| EXISTING ARCHITECTURE CONTROL | §3.5 SEC-PR10-003; sequences §5.6/§5.8; Agent MUST reject. |
| REQUIRED SECURITY CONTROL | SR-CMD-TICKET: mint binding only after confirm+RBAC; Agent enforce all minimum fields. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Missing/mismatched/expired binding → Agent reject; no DB write. |
| RESIDUAL RISK | None. |

## TM-AG-005

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-005` |
| ASSET | Control traffic confidentiality |
| TRUST BOUNDARY | B4 |
| THREAT | Eavesdropping on Agent↔Cloud cleartext |
| ATTACK / FAILURE SCENARIO | Cleartext control channel exposes commands, results, PII. |
| IMPACT | HIGH–CRITICAL disclosure. |
| EXISTING ARCHITECTURE CONTROL | §2.4 P-ENCRYPT; Founder outbound encrypted. |
| REQUIRED SECURITY CONTROL | SR-XPORT-TLS PENDING(#3): encrypted transport mandatory. |
| SEVERITY | **HIGH** |
| OWNER | Architect |
| VERIFICATION METHOD | #3 forbids cleartext; TLS (or equiv) evidence. |
| RESIDUAL RISK | None. |

## TM-AG-006

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AG-006` |
| ASSET | Customer network posture |
| TRUST BOUNDARY | B4 |
| THREAT | Cloud initiates inbound connection to customer Agent/DB ports |
| ATTACK / FAILURE SCENARIO | Design or bug dials customer 5432/27017 or requires inbound Agent control port. |
| IMPACT | CRITICAL — violates Founder hard requirement; expands exposure. |
| EXISTING ARCHITECTURE CONTROL | §2.3, P-OUTBOUND, ADR 0002; §8 AC. |
| REQUIRED SECURITY CONTROL | SR-XPORT-OUTBOUND: outbound-only; no exception without Founder+Security. |
| SEVERITY | **CRITICAL** |
| OWNER | Architect |
| VERIFICATION METHOD | Architecture/#3 ADR asserts outbound-only; no Cloud→customer DB dial. |
| RESIDUAL RISK | None — hard deny. |

## TM-ENR-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ENR-001` |
| ASSET | A5 enrollment secrets |
| TRUST BOUNDARY | B5 |
| THREAT | Enrollment proof theft → rogue Agent joins org/env |
| ATTACK / FAILURE SCENARIO | Long-lived enrollment token leaked from chat/ticket; attacker completes enrollment. |
| IMPACT | CRITICAL Agent impersonation at enrollment. |
| EXISTING ARCHITECTURE CONTROL | §5.1 enrollment; §2.4 properties. |
| REQUIRED SECURITY CONTROL | SR-ENROLL-*: short TTL, single-use, org+env pre-bind, audited; invalidate on use. PENDING(#3) format. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Reuse of enrollment proof fails; expired fails; audit present. |
| RESIDUAL RISK | Short theft window — LOW if TTL short. |

## TM-ENR-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ENR-002` |
| ASSET | A6 Agent binding |
| TRUST BOUNDARY | B6 |
| THREAT | Re-bind activated Agent to different org/env |
| ATTACK / FAILURE SCENARIO | API allows changing Agent org/env after activation, enabling cross-tenant/env deputy. |
| IMPACT | CRITICAL. |
| EXISTING ARCHITECTURE CONTROL | §3.4 #1 Agent bound exactly one org+one env at activation. |
| REQUIRED SECURITY CONTROL | SR-AGENT-BIND: immutable org+env after activation; re-enroll for change. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | PATCH agent org/env rejected. |
| RESIDUAL RISK | None. |

## TM-CONN-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-CONN-001` |
| ASSET | A14 connections; A1 credentials |
| TRUST BOUNDARY | B8 |
| THREAT | Connection substitution / confusion — command for conn A executed on conn B |
| ATTACK / FAILURE SCENARIO | Tampered connection_id or Agent bug applies mutation using wrong local credential. |
| IMPACT | CRITICAL wrong-database mutation. |
| EXISTING ARCHITECTURE CONTROL | §3.4 #5 Agent rejects connection_id not configured for org/env; SEC-PR10-010; §3.5 binds connection_id. |
| REQUIRED SECURITY CONTROL | SR-CONN-BIND: Agent verifies local config + org/env match before any sensitive op. |
| SEVERITY | **CRITICAL** |
| OWNER | Agent |
| VERIFICATION METHOD | Command with foreign connection_id rejected; no DB touch. |
| RESIDUAL RISK | None. |

## TM-CONN-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-CONN-002` |
| ASSET | A1 all credentials on Agent host |
| TRUST BOUNDARY | B8/B7 |
| THREAT | Single Agent compromise exposes all configured connection credentials (blast radius) |
| ATTACK / FAILURE SCENARIO | AT3 reads local secret store; obtains every DB password for that Agent's connections; mutates all DBs. |
| IMPACT | HIGH residual concentration risk (product default topology). |
| EXISTING ARCHITECTURE CONTROL | §7 compensating controls: per-connection least-privilege DB creds; revoke enrollment + invalidate queued bindings; incident runbook hook. FA-CONN-BLAST placeholder. |
| REQUIRED SECURITY CONTROL | SR-CONN-BLAST, SR-DB-LEAST + formal FA-CONN-BLAST disposition (fa-conn-blast.md). Do NOT silently accept. |
| SEVERITY | **HIGH** |
| OWNER | Founder |
| VERIFICATION METHOD | Runbook + revoke test; least-privilege guidance documented; FA recorded or topology change. |
| RESIDUAL RISK | HIGH residual if multi-conn retained — requires Founder acceptance after compensating controls. |

## TM-CONN-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-CONN-003` |
| ASSET | A14 readiness metadata |
| TRUST BOUNDARY | B7/B8 |
| THREAT | Stale readiness — Cloud believes connection ready after local secret removed |
| ATTACK / FAILURE SCENARIO | Admin removes local secret; Cloud still dispatches mutations; confusing failures or retries. |
| IMPACT | MEDIUM availability / mistaken ops pressure. |
| EXISTING ARCHITECTURE CONTROL | §5.2 readiness reporting via agentcontrol→connections. |
| REQUIRED SECURITY CONTROL | SR-CONN-HEALTH: Agent reports not-ready; Cloud refuses mutating dispatch when not ready. |
| SEVERITY | **MEDIUM** |
| OWNER | Agent |
| VERIFICATION METHOD | Remove secret → readiness false → execute not dispatched. |
| RESIDUAL RISK | LOW race. |

## TM-SEC-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-SEC-001` |
| ASSET | A1 customer DB credentials |
| TRUST BOUNDARY | B7 / Cloud |
| THREAT | Customer DB credentials stored, logged, or cached in Cloud |
| ATTACK / FAILURE SCENARIO | Connection form posts password to Cloud API; or Agent echoes password in result/audit. |
| IMPACT | CRITICAL Founder violation; mass credential breach if Cloud compromised. |
| EXISTING ARCHITECTURE CONTROL | §1, §2.3, ADR 0002, §8 AC; product §10 hard requirement. |
| REQUIRED SECURITY CONTROL | SR-SECRET-CLOUD-DENY, SR-LOG-NOSECRET: API schema excludes secrets; tests assert absence in Cloud DB/logs/audit. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Cloud DB scan / API contract tests; negative tests. |
| RESIDUAL RISK | None — hard deny. |

## TM-SEC-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-SEC-002` |
| ASSET | A1; A5; A4 |
| TRUST BOUNDARY | Logs / errors / audit payloads |
| THREAT | Sensitive leakage via logs, exception messages, or audit before/after |
| ATTACK / FAILURE SCENARIO | Stack traces include connection URI with password; audit stores secrets; Agent logs tokens. |
| IMPACT | HIGH credential/session exfil via log aggregation. |
| EXISTING ARCHITECTURE CONTROL | §3.1 shared logging no secrets; §8 AC; §5.7 audit redaction expectation. |
| REQUIRED SECURITY CONTROL | SR-LOG-NOSECRET, SR-AUDIT-REDACT: structured allowlists; secret scanners in CI sample paths. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Inject secret in error path → redacted in logs/audit. |
| RESIDUAL RISK | LOW misconfig. |

## TM-SEC-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-SEC-003` |
| ASSET | A1 on Agent host |
| TRUST BOUNDARY | B7 |
| THREAT | DB credential theft from Agent host filesystem/memory |
| ATTACK / FAILURE SCENARIO | Malware or insider reads Agent secret files. |
| IMPACT | CRITICAL for that connection (and siblings under TM-CONN-002). |
| EXISTING ARCHITECTURE CONTROL | Credentials customer-side only (ADR 0002). |
| REQUIRED SECURITY CONTROL | SR-SECRET-AGENT: local secret store guidance; file permissions; no secrets in Agent logs. |
| SEVERITY | **CRITICAL** |
| OWNER | Agent |
| VERIFICATION METHOD | Agent docs + default file mode; log redaction tests. |
| RESIDUAL RISK | Customer host compromise — FA-CONN-BLAST. |

## TM-SEC-004

| Field | Value |
| --- | --- |
| THREAT ID | `TM-SEC-004` |
| ASSET | A5 Agent secrets; A4 sessions at rest |
| TRUST BOUNDARY | Cloud datastore / backups |
| THREAT | Cloud backup/export exposes Agent secrets or session material |
| ATTACK / FAILURE SCENARIO | Uncontrolled backup access yields Agent credentials or session tokens. |
| IMPACT | HIGH. |
| EXISTING ARCHITECTURE CONTROL | ADR 0003 Cloud PostgreSQL; infra later. |
| REQUIRED SECURITY CONTROL | SR-SECRET-ATREST: encrypt secrets at rest; backup access control (Platform/infra epic). |
| SEVERITY | **HIGH** |
| OWNER | Platform |
| VERIFICATION METHOD | Infra checklist when hosting exists. |
| RESIDUAL RISK | Deferred infra — track; block prod without plan. |

## TM-INJ-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-INJ-001` |
| ASSET | A2 customer DB |
| TRUST BOUNDARY | B7 Agent↔DB |
| THREAT | SQL injection / NoSQL injection via search or Action parameters |
| ATTACK / FAILURE SCENARIO | User-supplied filter concatenated into SQL/Mongo; attacker runs arbitrary query/mutation. |
| IMPACT | CRITICAL data breach / destruction. |
| EXISTING ARCHITECTURE CONTROL | §1 no arbitrary SQL/Mongo; §5.4 structured search; §8 AC. |
| REQUIRED SECURITY CONTROL | SR-INJECT-*: parameterized driver APIs only; reject raw query strings from UI/Cloud. |
| SEVERITY | **CRITICAL** |
| OWNER | Agent |
| VERIFICATION METHOD | Injection payloads in filters/Action args cannot alter query structure; Code Review. |
| RESIDUAL RISK | None if parameterized. |

## TM-INJ-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-INJ-002` |
| ASSET | A2; product promise |
| TRUST BOUNDARY | B1/B9/B7 |
| THREAT | Arbitrary SQL/Mongo console or query escape hatch |
| ATTACK / FAILURE SCENARIO | Feature or debug endpoint accepts freeform SQL/Mongo from UI. |
| IMPACT | CRITICAL — violates Founder non-goals. |
| EXISTING ARCHITECTURE CONTROL | Product §16 non-goals; architecture §1/§8. |
| REQUIRED SECURITY CONTROL | SR-QUERY-DENY: no arbitrary query APIs in V1; Code Review rejects escape hatches. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | API surface review — no raw query endpoints. |
| RESIDUAL RISK | None — hard deny. |

## TM-ACT-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-001` |
| ASSET | A7 unsafe Action definitions |
| TRUST BOUNDARY | B9 |
| THREAT | Unsafe Action definitions (over-broad mutation, missing bounds, hidden side effects) |
| ATTACK / FAILURE SCENARIO | Admin defines Action that updates unbounded rows or uses unsafe templates. |
| IMPACT | HIGH mass damage. |
| EXISTING ARCHITECTURE CONTROL | Actions module orchestration; blast risk in architecture §7 partial execution notes. |
| REQUIRED SECURITY CONTROL | SR-ACT-*, SR-BLAST-01: declare upper bound on affected records; Agent refuse unbounded; define RBAC gated. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Over-limit Action aborted; definition validation tests. |
| RESIDUAL RISK | Admin malice within define privilege — audit + define RBAC. |

## TM-ACT-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-002` |
| ASSET | A2 records |
| TRUST BOUNDARY | B9 |
| THREAT | Excessive affected records / mass mutation fan-out |
| ATTACK / FAILURE SCENARIO | Action preview shows N but execute affects >>N; or no max. |
| IMPACT | HIGH availability/integrity damage. |
| EXISTING ARCHITECTURE CONTROL | Product Actions controlled; Security SR-BLAST in draft requirements. |
| REQUIRED SECURITY CONTROL | SR-BLAST-01/02: hard caps; rate-limit executions per actor/org/env. |
| SEVERITY | **HIGH** |
| OWNER | Agent |
| VERIFICATION METHOD | Cap enforcement at Agent; Cloud rate-limit tests. |
| RESIDUAL RISK | None if caps enforced. |

## TM-ACT-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-003` |
| ASSET | A2; preview path |
| TRUST BOUNDARY | B9 |
| THREAT | Preview mutates customer database |
| ATTACK / FAILURE SCENARIO | Preview path incorrectly issues UPDATE/DELETE. |
| IMPACT | CRITICAL — Founder preview must not mutate. |
| EXISTING ARCHITECTURE CONTROL | §5.5 preview read-only; §8 AC. |
| REQUIRED SECURITY CONTROL | SR-PREV-READONLY: Agent preview handlers read-only; tests assert no write. |
| SEVERITY | **CRITICAL** |
| OWNER | Agent |
| VERIFICATION METHOD | DB write counters / trigger during preview = 0. |
| RESIDUAL RISK | None. |

## TM-ACT-004

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-004` |
| ASSET | Preview honesty; A2 |
| TRUST BOUNDARY | B9 |
| THREAT | Preview overclaims authority or hides limitations |
| ATTACK / FAILURE SCENARIO | UI presents non-authoritative/stale preview as exact final result. |
| IMPACT | HIGH user deceived into unsafe confirm. |
| EXISTING ARCHITECTURE CONTROL | §5.5–5.6 honesty flags; CR-PR10-004 confirm presents limitation flags. |
| REQUIRED SECURITY CONTROL | SR-PREV-HONEST: flags mandatory in API+UI; confirm gate requires acknowledgment when flags present. |
| SEVERITY | **HIGH** |
| OWNER | Frontend |
| VERIFICATION METHOD | Limitation flags visible at confirm; cannot confirm without ack when required. |
| RESIDUAL RISK | FA-PREV-RACE optional after controls. |

## TM-ACT-005

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-005` |
| ASSET | A2; execute path |
| TRUST BOUNDARY | B9/B10 |
| THREAT | Preview/execute TOCTOU — state changed between preview and execute |
| ATTACK / FAILURE SCENARIO | Record changed after preview; execute applies based on stale assumptions without revalidation. |
| IMPACT | HIGH incorrect mutation. |
| EXISTING ARCHITECTURE CONTROL | §5.6 TOCTOU rules SEC-PR10-004: execute-time revalidation OR safe preview bind. |
| REQUIRED SECURITY CONTROL | SR-PREV-TOCTOU: implement revalidation or bind; abort on unsafe change; honest failure. |
| SEVERITY | **HIGH** |
| OWNER | Agent |
| VERIFICATION METHOD | Change row after preview → execute aborts or safe bind invalidates. |
| RESIDUAL RISK | MEDIUM residual race → FA-PREV-RACE only after controls. |

## TM-ACT-006

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-006` |
| ASSET | A10 binding mint |
| TRUST BOUNDARY | B9 |
| THREAT | Execute without explicit confirmation / binding minted from preview alone |
| ATTACK / FAILURE SCENARIO | Single-click preview triggers mutate binding without distinct confirm. |
| IMPACT | HIGH accidental/CSRF-amplified mutation. |
| EXISTING ARCHITECTURE CONTROL | §5.6 Cloud confirm gate BEFORE minting §3.5 binding (CR-PR10-004). |
| REQUIRED SECURITY CONTROL | SR-CONF-*: distinct confirm step; production distinguishable. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | No binding minted without confirm event. |
| RESIDUAL RISK | None. |

## TM-ACT-007

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-007` |
| ASSET | A11 operation_id |
| TRUST BOUNDARY | B4/B10 |
| THREAT | Duplicate execution under at-least-once delivery |
| ATTACK / FAILURE SCENARIO | Command delivered twice; Agent mutates twice. |
| IMPACT | CRITICAL duplicate side effects. |
| EXISTING ARCHITECTURE CONTROL | §2.4 P-REPLAY; §5.6.1 duplicate results safe; #3 owns delivery semantics. |
| REQUIRED SECURITY CONTROL | SR-IDEM-*: Agent dedupe by operation_id; Cloud idempotent terminal apply. PENDING(#3) wire. |
| SEVERITY | **CRITICAL** |
| OWNER | Agent |
| VERIFICATION METHOD | Double delivery → single mutation. |
| RESIDUAL RISK | None if idempotent. |

## TM-ACT-008

| Field | Value |
| --- | --- |
| THREAT ID | `TM-ACT-008` |
| ASSET | A11 results |
| TRUST BOUNDARY | B4 / §5.6.1 |
| THREAT | Lost execution results / false success or failure terminalization |
| ATTACK / FAILURE SCENARIO | Mutation committed on DB but result never reaches Cloud; Cloud marks succeeded/failed incorrectly or invents terminal state. |
| IMPACT | CRITICAL integrity of ops + audit lie. |
| EXISTING ARCHITECTURE CONTROL | §5.6.1 durable Agent results; lifecycle pending/succeeded/failed/unknown; no silent terminalization; reconciliation. |
| REQUIRED SECURITY CONTROL | SR-OP-*: implement lifecycle honesty; unknown/reconciliation-required; audit honesty. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Drop result packets → Cloud stays pending/unknown; reconnect reconciles; never silent success. |
| RESIDUAL RISK | PENDING(#3) wire details. |

## TM-RB-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-RB-001` |
| ASSET | Rollback operations |
| TRUST BOUNDARY | B9 |
| THREAT | Rollback misuse — offered when unsafe or without authz/audit |
| ATTACK / FAILURE SCENARIO | UI shows rollback for non-reversible Action; or rollback skips RBAC/audit; or stale rollback after state drift. |
| IMPACT | HIGH data corruption / cover-up. |
| EXISTING ARCHITECTURE CONTROL | §5.8 rollback only when declared reversible + safety checks; §3.5 binding; audited. |
| REQUIRED SECURITY CONTROL | SR-RB-*: unavailable when unsafe; authorize+audit; Agent re-check state; link original operation_id. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Non-reversible Action has no rollback API; unsafe state aborts; audit linked. |
| RESIDUAL RISK | None. |

## TM-AUD-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AUD-001` |
| ASSET | A12 audit history |
| TRUST BOUNDARY | B11 |
| THREAT | Audit tamper or delete by ops / app roles |
| ATTACK / FAILURE SCENARIO | Ops user UPDATE/DELETE audit rows via API or app DB role. |
| IMPACT | CRITICAL repudiation / cover-up. |
| EXISTING ARCHITECTURE CONTROL | §5.7 storage-level immutability (SEC-PR10-006); ops cannot mutate history; ≥1 year retention target. |
| REQUIRED SECURITY CONTROL | SR-AUDIT-IMMUT: DB grants/triggers/immutable store; no update/delete API for normal roles. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | App role cannot UPDATE/DELETE audit; API denies. |
| RESIDUAL RISK | Superuser insider R4 HIGH — infra. |

## TM-AUD-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-AUD-002` |
| ASSET | A12 |
| TRUST BOUNDARY | B11 |
| THREAT | Mutation attempt without correlated audit (success, fail, unknown) |
| ATTACK / FAILURE SCENARIO | Execute path skips audit on failure or unknown; intent-only without terminal honesty. |
| IMPACT | HIGH repudiation gap. |
| EXISTING ARCHITECTURE CONTROL | §5.7 every mutation attempt; §5.6.1 honest unknown. |
| REQUIRED SECURITY CONTROL | SR-AUDIT-MUT: audit intent + terminal/unknown events keyed by operation_id. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Fail and unknown paths produce audit rows. |
| RESIDUAL RISK | None. |

## TM-Q-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-Q-001` |
| ASSET | A9 command queue |
| TRUST BOUNDARY | B12 |
| THREAT | Offline queue abuse — stale mutating command executes after revoke |
| ATTACK / FAILURE SCENARIO | Admin revokes user/Action/Agent; queued execute still drains when Agent returns. |
| IMPACT | HIGH unauthorized late mutation. |
| EXISTING ARCHITECTURE CONTROL | §5.9 cancel-on-revoke; TTL/depth control points (SEC-PR10-008). |
| REQUIRED SECURITY CONTROL | SR-QUEUE-REAUTH / SR-QUEUE-*: invalidate bindings on revoke; TTL; UX backlog visibility. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | Revoke then Agent reconnect → command non-executable. |
| RESIDUAL RISK | None. |

## TM-Q-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-Q-002` |
| ASSET | A9; Cloud storage |
| TRUST BOUNDARY | B12 |
| THREAT | Queue growth DoS / storage exhaustion while Agents offline |
| ATTACK / FAILURE SCENARIO | Attacker or bug enqueues unbounded commands. |
| IMPACT | MEDIUM–HIGH availability. |
| EXISTING ARCHITECTURE CONTROL | §5.9 max depth/TTL required as control points. |
| REQUIRED SECURITY CONTROL | SR-QUEUE-LIMIT, SR-RL-*: depth limits; rate limits on enqueue. |
| SEVERITY | **MEDIUM** |
| OWNER | Backend |
| VERIFICATION METHOD | Enqueue beyond depth rejected. |
| RESIDUAL RISK | LOW. |

## TM-Q-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-Q-003` |
| ASSET | A9 cross-tenant queue |
| TRUST BOUNDARY | B2/B12 |
| THREAT | Wrong Agent drains another org's commands |
| ATTACK / FAILURE SCENARIO | Queue scoping bug delivers Org B commands to Org A Agent. |
| IMPACT | CRITICAL cross-tenant + confused deputy. |
| EXISTING ARCHITECTURE CONTROL | P-ORG-BIND, §3.4, queue scope with agent binding. |
| REQUIRED SECURITY CONTROL | SR-QUEUE-SCOPE: commands scoped to agent_id+org+env; Agent rejects mismatch. |
| SEVERITY | **CRITICAL** |
| OWNER | Backend |
| VERIFICATION METHOD | Cross-agent drain impossible in tests. |
| RESIDUAL RISK | None. |

## TM-DOS-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-DOS-001` |
| ASSET | Auth and Action APIs |
| TRUST BOUNDARY | B1 |
| THREAT | DoS / exhaustion on auth or mutation endpoints |
| ATTACK / FAILURE SCENARIO | Credential stuffing or execute floods. |
| IMPACT | MEDIUM availability; may amplify auth attacks. |
| EXISTING ARCHITECTURE CONTROL | Redis deferred (ADR 0003); PostgreSQL sessions/outbox. |
| REQUIRED SECURITY CONTROL | SR-RL-AUTH, SR-BLAST-02: rate limits; backoff. |
| SEVERITY | **MEDIUM** |
| OWNER | Platform |
| VERIFICATION METHOD | Burst login/execute throttled. |
| RESIDUAL RISK | Scale limits without Redis — revisit ADR 0003. |

## TM-HST-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-HST-001` |
| ASSET | A1; A2; A5 |
| TRUST BOUNDARY | B7/B8 |
| THREAT | Compromised Agent host used for credential exfil and multi-DB mutate |
| ATTACK / FAILURE SCENARIO | AT3 fully controls Agent process; exfiltrates secrets; may attempt to spoof results. |
| IMPACT | CRITICAL for customer env; HIGH blast if multi-conn. |
| EXISTING ARCHITECTURE CONTROL | §7 blast compensating controls; revoke; least privilege. |
| REQUIRED SECURITY CONTROL | SR-AGENT-AUTH revoke; SR-DB-LEAST; FA-CONN-BLAST; result authenticity PENDING(#3). |
| SEVERITY | **CRITICAL** |
| OWNER | Founder |
| VERIFICATION METHOD | Revoke stops further command receipt; runbook exercised in drill. |
| RESIDUAL RISK | HIGH multi-conn — FA-CONN-BLAST. |

## TM-HST-002

| Field | Value |
| --- | --- |
| THREAT ID | `TM-HST-002` |
| ASSET | Cloud control plane data |
| TRUST BOUNDARY | Cloud |
| THREAT | Compromised Cloud application → issue forged bindings / read PII in transit results |
| ATTACK / FAILURE SCENARIO | Attacker gains Cloud app RCE or admin; mints bindings; reads search results. |
| IMPACT | CRITICAL tenant-wide. |
| EXISTING ARCHITECTURE CONTROL | Defense in depth: Agent still checks bindings; no DB creds in Cloud limits credential theft. |
| REQUIRED SECURITY CONTROL | Platform hardening (later); least-privilege Cloud DB roles; audit of binding mint; Security review of admin paths. |
| SEVERITY | **CRITICAL** |
| OWNER | Platform |
| VERIFICATION METHOD | Infra/prod gates; binding mint audited. |
| RESIDUAL RISK | Cloud compromise is catastrophic for control plane — accepted class of SaaS risk with mitigations; no silent ignore of binding audit. |

## TM-HST-003

| Field | Value |
| --- | --- |
| THREAT ID | `TM-HST-003` |
| ASSET | A12; Cloud PostgreSQL |
| TRUST BOUNDARY | Cloud datastore |
| THREAT | Malicious insider with Cloud DB superuser |
| ATTACK / FAILURE SCENARIO | Insider reads all tenant metadata/results; tampers audit if superuser bypasses app controls. |
| IMPACT | HIGH–CRITICAL. |
| EXISTING ARCHITECTURE CONTROL | SR-AUDIT-IMMUT at app role; storage-level controls. |
| REQUIRED SECURITY CONTROL | Platform: break-glass logging; limited superuser; backup ACLs. Track for infra. |
| SEVERITY | **HIGH** |
| OWNER | Platform |
| VERIFICATION METHOD | Ops runbooks; access reviews when hosted. |
| RESIDUAL RISK | HIGH residual until infra — document R4. |

## TM-SSRF-001

| Field | Value |
| --- | --- |
| THREAT ID | `TM-SSRF-001` |
| ASSET | Cloud/Agent network |
| TRUST BOUNDARY | B1/B7 |
| THREAT | SSRF via connection descriptors, webhooks, or URL fields |
| ATTACK / FAILURE SCENARIO | If any Cloud-side fetch of user-supplied URLs exists, attacker hits internal metadata; or Agent follows malicious redirect. |
| IMPACT | HIGH internal network exposure. |
| EXISTING ARCHITECTURE CONTROL | V1 Cloud must not dial customer DB; connection metadata non-secret descriptors — watch for URL fetch features. |
| REQUIRED SECURITY CONTROL | SR-SSRF: no Cloud SSRF-prone fetches in V1; if Agent connects only via configured DB drivers to configured hosts; block link-local/metadata targets for any HTTP client. |
| SEVERITY | **HIGH** |
| OWNER | Backend |
| VERIFICATION METHOD | API surface review; any HTTP client blocklist tests. |
| RESIDUAL RISK | LOW if no URL fetch features. |

---

## Severity summary

| Severity | Count |
| --- | --- |
| CRITICAL | 23 |
| HIGH | 24 |
| MEDIUM | 3 |
| LOW | 0 |
| **Total** | **50** |

## Theme coverage index

| Theme | Threat IDs |
| --- | --- |
| BOLA/IDOR / cross-tenant | TM-TEN-001..004, TM-Q-003 |
| Staging/prod crossover | TM-ENV-001..002 |
| Priv-esc / broken authz / define≠run | TM-RBAC-001..003 |
| Confused deputy | TM-AG-004 |
| Forged / replayed / expired commands | TM-AG-002..003 |
| Stolen Agent creds / impersonation | TM-AG-001, TM-ENR-001 |
| Compromised Agent / Cloud / insider | TM-HST-001..003 |
| Credential leakage / exfil | TM-SEC-001..004 |
| SQLi/NoSQLi / arbitrary query | TM-INJ-001..002 |
| Unsafe Action defs / excessive records | TM-ACT-001..002 |
| Preview mutate / honesty / TOCTOU | TM-ACT-003..005 |
| Confirm / duplicate execution / lost results / false terminal | TM-ACT-006..008 |
| Audit tamper/delete / missing audit | TM-AUD-001..002 |
| Rollback misuse/stale | TM-RB-001 |
| Connection substitution / org-env-connection binding | TM-CONN-001, TM-ENV-*, TM-ENR-002, TM-AG-004 |
| Multi-connection blast (FA-CONN-BLAST) | TM-CONN-002, TM-HST-001 |
| Offline queue abuse | TM-Q-001..003 |
| DoS/exhaustion | TM-DOS-001, TM-Q-002 |
| Logs/errors sensitive leakage | TM-SEC-002 |
| SSRF | TM-SSRF-001 |
| XSS / CSRF | TM-UI-001..002 |
| User auth / sessions / invites / reset | TM-AUTH-001..003 |

## Document history

| Date | Change |
| --- | --- |
| 2026-09-19 | Initial formal register for Issue #4 |
