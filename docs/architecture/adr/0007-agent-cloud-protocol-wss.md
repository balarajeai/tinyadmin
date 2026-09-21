# ADR 0007: Agent↔Cloud protocol — WebSocket over TLS + signed envelopes

- **Status:** Proposed — Issue #3 **Security gate PASS**; Independent Code Review **remediation / re-review pending** (CR-PR12). Not overall product security approval; not #4/#5/#6 completion; not production readiness.
- **Date:** 2026-09-19
- **Related issues:** #3 (governing), #2 (architecture), #4 / #5 (Security), #6 (domain model fields)
- **Conforms to:** [ADR 0006](./0006-cloud-agent-security-properties.md), architecture §2.4 / §3.4–§3.5 / §5.6.1

## Context

TinyAdmin Agents must reach Cloud **outbound-only**, without exposing customer Postgres/Mongo ports. Issue #2 fixed normative security properties (ADR 0006) and operation-result durability (§5.6.1) but deferred concrete transport, authn, and delivery mechanics to Issue #3.

V1 constraints include Contabo/VPS-style deployment, reverse proxies, Java/Spring Cloud, Go Agent, multi-tenant org+env binding (one Agent → one org + one env), Cloud-issued mutation authorization bindings, and non–best-effort result delivery.

## Decision

### Transport (V1)

1. **Primary session channel:** **WebSocket over TLS (`wss://`)** initiated **only by the Agent** to TinyAdmin Cloud.
2. **Bootstrap / fallback HTTP:** **HTTPS** endpoints for enrollment activation, credential rotation helpers, and **durable result / reconciliation POSTs** when the WebSocket is unavailable (same authn and envelope rules).
3. **TLS:** TLS 1.2+ required; TLS 1.3 preferred. Cleartext forbidden (P-ENCRYPT / SR-XPORT-TLS).
4. **No inbound customer Agent ports** and **no Cloud→customer DB sockets** (P-OUTBOUND / SR-XPORT-OUTBOUND).

### Authentication and identity (V1)

1. **Enrollment:** Authorized Cloud user creates an enrollment intent bound to exactly one `organization_id` + `environment_id` + intended `agent_id`. Cloud issues a **one-time enrollment token** (short TTL).
2. **Device key:** At activation, the Agent generates an **Ed25519 keypair**, retains the private key in customer-side secret storage, and presents the public key + enrollment token to Cloud over HTTPS.
3. **Activation:** Cloud verifies the enrollment token, stores `agent_id` → public key + immutable org/env binding, marks Agent active, audits the event, and invalidates the enrollment token.
4. **Session authn:** Agent opens `wss://` and proves possession of the private key via a **challenge–response** (Cloud nonce signed by Agent). Cloud issues a short-lived **session capability** bound to `agent_id`/org/env (not a substitute for per-command authorization bindings).
5. **Cloud authenticity (SR-XPORT-MUTAUTH):** Agent authenticates Cloud via **TLS server authentication** (public CA or pinned Cloud TLS certs for customer-managed installs) **and** verifies **Cloud signatures** on command envelopes using a Cloud command-signing public key distributed out-of-band / at enrollment.
6. **Not sufficient alone:** org-wide static shared secrets, unsigned WebSocket frames, or “TLS only” without Agent identity and signed command envelopes.

### Authorization binding and command integrity (SEC-PR12-001)

1. Every **mutating** command (execute, approved-field mutate, rollback) **MUST** include a Cloud-issued **signed authorization envelope** (Ed25519). This is the Issue #3 encoding of architecture §3.5 / SR-CMD-TICKET.
2. **V1 signature-coverage approach (option B):** The Cloud signature **MUST** cover a frozen set of authorization fields **including** `mutation_payload_sha256` — a **SHA-256** digest of the **canonical mutation payload** (targets, parameters/requested values, Action or approved-field operation identity, and affected-record constraint). The Agent **MUST** recompute the digest from the received mutation payload under the same canonicalization, verify the Cloud signature over the envelope fields (including that digest), and **fail closed** on mismatch or malformed canonical form. The Agent **MUST NEVER** execute a mutation payload not covered by a verified authorization envelope.
3. **Canonicalization contract (normative):** Canonical mutation payload is **UTF-8 JSON** produced by **RFC 8785 JSON Canonicalization Scheme (JCS)**. The object **MUST** contain exactly these keys (sorted by JCS): `action_or_field_op`, `targets`, `parameters`, `max_affected_records`. Unknown keys forbidden. Execute/field-edit vs **rollback** use frozen profiles in protocol §6.1.1 (rollback: `type=rollback` + `parent_operation_id`; targets = parent copy; `parameters={}`; `max_affected_records` = parent bound).
4. **Envelope fields covered by Cloud signature (minimum):** `kid`, `operation_id`, `actor_id`, `organization_id`, `environment_id`, `agent_id`, `connection_id`, `action_or_field_op` (same identity as in payload), `mutation_payload_sha256`, `max_affected_records`, `iat`, `exp`, plus Cloud signature over the envelope’s canonical bytes (JCS of envelope body excluding the signature field itself).
5. **Sensitive non-mutating** commands (discovery, search, preview) **MUST** carry org/env/agent/connection context and Cloud authenticity/integrity; they **SHOULD** carry a signed read grant. If a body is present, V1 **SHOULD** bind a payload digest similarly when the body influences authorization.
6. Agent **MUST** reject envelopes that fail signature, unknown `kid`, expiry (with clock-skew policy), or binding mismatch (org/env/agent/connection/Action).
7. **Replay resistance:** `operation_id` is unique; Agent persists operation execution state (see delivery semantics); duplicate mutate delivery after a durable terminal decision → return prior result / no second mutate. Expired envelopes are rejected (not executed).

### Delivery semantics (SEC-PR12-003 — narrowed)

1. **Commands:** Cloud → Agent delivery is **at-least-once**.
2. **Results:** Agent → Cloud result delivery is **at-least-once until authenticated Cloud `result_ack`**.
3. **Duplicate commands after a durable Agent execution decision:** MUST NOT cause a second customer-DB mutation (safe/no-op returning prior outcome).
4. **Not claimed:** unqualified **exactly-once mutation effects** across all crash windows or across independent stores (Agent local state vs customer PostgreSQL/MongoDB). Customer DB commit followed by Agent crash **before** durable completion state MAY yield Cloud `unknown` / `reconciliation_required` and requires reconciliation (engine-specific strategies). Blind re-execution when prior outcome is indeterminate is **forbidden**.
5. Normative Agent operation execution state machine: see protocol doc §6.2 / §10.

### Revocation of delivered-but-unexecuted mutates (SEC-PR12-002)

1. Agent/Action/auth revocation **MUST** terminate applicable Agent sessions where Cloud can do so.
2. Cloud **MUST** cancel matching Cloud-outbox mutates and, when a session exists, send **cancel** notifications for affected `operation_id`s.
3. Agent **MUST** track revocation/cancellation state; **MUST** re-check command validity **immediately before** execution; **MUST NOT** execute locally queued mutates after relevant revocation/cancellation is known; **MUST** discard expired authorization.
4. On reconnect, Agent **MUST** complete authoritative cancel/revoke synchronization **before** any pending mutate execution (see protocol §8); fail closed if sync incomplete.
5. Mutating authorization envelopes use a **short-lived TTL policy class** (minutes-scale upper bound relative to operational revoke detection — exact numeric default is Platform/Security tunable; property is “short-lived,” not long-lived offline authority).
6. **Residual race:** a mutate that **already committed** on the customer DB cannot be undone by revoke; revoke does not reverse commits. A narrow race remains if execution begins after last cancel opportunity but before revoke knowledge — document as residual; outcome must surface honestly (`succeeded` with audit, or `unknown` if indeterminate).

### Offline queue, revoke, ordering, backpressure

1. Cloud persists outbound commands in PostgreSQL outbox (modular monolith — no separate broker service).
2. **Depth + TTL** required (SR-QUEUE-LIMIT). V1 defaults (tunable): max **1000** pending mutating commands per Agent; **outbox** mutating command retention TTL **24h**; overflow **fail closed**. Separately, **authorization envelope `exp`** follows the **short-lived TTL policy class** (SEC-PR12-002) — do not treat 24h outbox retention as 24h execute authority.
3. On Agent credential revoke, role revoke, or Action disable: Cloud-outbox mutates for that scope are **cancelled**; sessions terminated; cancel notifications sent when connected; Agent-local rules in “Revocation of delivered-but-unexecuted mutates” apply (SR-AGENT-ROT-02 / SR-QUEUE-REAUTH).
4. **Ordering:** per-Agent **best-effort FIFO** for dispatch; **no cross-connection total order guarantee**. Safety relies on signed payload binding + execution state machine, not global ordering.
5. **Backpressure:** if outbox depth exceeds soft threshold, Cloud surfaces Agent unhealthy / degraded and slows accept of new mutates; hard limit fails closed.

### Key rotation, ack authenticity, clock skew, key storage (SEC-PR12-004/005/006/008)

1. Envelopes carry `kid`; Agent **MUST** reject unknown signing keys (fail closed). Cloud **MAY** advertise an overlap accept-set during rotation; Agents accept old+new `kid` only during documented overlap, then old retired.
2. `result_ack` **MUST** be authenticated as Cloud (session-bound MAC/signature or Cloud-signed ack) and correlated by `operation_id`; Agent **MUST** ignore unauthenticated acks.
3. Envelope time validity uses Cloud `iat`/`exp` with an explicit **bounded clock-skew tolerance**; outside tolerance → fail closed. Numeric skew bound is Platform/Security tunable; V1 must document the configured bound in ops runbooks.
4. Agent private key **MUST** use platform-appropriate protected storage (OS keychain/secret file with restricted perms/HSM as available). **Ed25519** is frozen for V1 signing; envelopes are versioned for future algorithm agility. Cloud TLS **cert pinning** remains optional unless Security later requires it.

## Alternatives considered

| Option | Pros | Cons | Verdict |
| --- | --- | --- | --- |
| **WSS + signed envelopes + HTTPS fallback (chosen)** | Outbound-only; low latency push; Spring/Go support; works behind nginx with sticky/upgrade; no extra broker; fits Contabo | Need reconnect/backoff design; proxy idle timeouts | **Select for V1** |
| **HTTPS long-poll / short poll only** | Simplest firewall story; easy to debug with curl | Higher latency; more load; long-poll proxy pitfalls; still need durable result POST | Reject as sole channel; retained as **fallback/reconciliation** |
| **gRPC bidirectional streaming** | Strong contracts; efficient | HTTP/2 + proxy complexity on small VPS; harder casual debug; heavier Agent ops | Reject for V1 |
| **MQTT / message broker** | Mature device patterns | Extra broker = unjustified infra/service for V1 modular monolith | Reject |
| **mTLS-only per Agent cert (no app envelopes)** | Strong transport identity | Cert lifecycle ops heavy on Contabo; still need app-level authz binding for confused-deputy; reverse-proxy mTLS awkward | Reject as sole design; **optional later** hardening |
| **Inbound Agent webhook / Cloud dials Agent** | Simple Cloud push | Violates outbound-only / customer firewall goals | Reject |
| **Org-wide static bearer token** | Easy | Fails P-AGENT-ID / SR-AGENT-AUTH | Reject |

## Consequences

- **Positive:** Concrete V1 path satisfying ADR 0006 and §5.6.1; implementable with Spring WebSocket + Go gorilla/websocket (or equivalent); no new microservice; Contabo-friendly with TLS termination.
- **Negative:** Must handle WS reconnect, proxy timeouts, dual-path result ack, cancel/revoke races, and honest `unknown` reconciliation; dual crypto (TLS + Ed25519 envelopes) is intentional defense in depth.
- **Follow-ups:** Security review of this ADR; domain model (#6) for outbox/result/agent key tables; Backend/Agent implementation after gates; Platform TLS and reverse-proxy WebSocket config.

## Related documents

- [agent-cloud-protocol-v1.md](../agent-cloud-protocol-v1.md) — flows, failure modes, AC, handoffs
- ADR 0002, ADR 0006, Issue #2 architecture on `main`
