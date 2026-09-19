# ADR 0007: Agent↔Cloud protocol — WebSocket over TLS + signed envelopes

- **Status:** Proposed (Security review required — CRITICAL)
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

### Authorization binding and command integrity

1. Every **mutating** command (execute, approved-field mutate, rollback) **MUST** include a Cloud-issued **signed authorization envelope** containing at least: `operation_id`, `actor_id`, `organization_id`, `environment_id`, `agent_id`, `connection_id`, Action or approved-field operation identity, `exp` (expiry), `iat`, and Cloud signature (Ed25519 or equivalent). This is the Issue #3 encoding of architecture §3.5 / SR-CMD-TICKET.
2. **Sensitive non-mutating** commands (discovery, search, preview) **MUST** carry org/env/agent/connection context and Cloud authenticity/integrity; they **SHOULD** carry a signed read grant / envelope (SR-CMD-TICKET SHOULD).
3. Agent **MUST** reject envelopes that fail signature, expiry, or binding mismatch (org/env/agent/connection/Action).
4. **Replay resistance:** `operation_id` is unique; Agent persists executed `operation_id`s for a retention window; duplicate mutate delivery → **idempotent no-op** returning prior result (SR-IDEM / P-REPLAY). Expired envelopes are rejected (not executed).

### Delivery semantics

1. **Commands:** Cloud → Agent delivery is **at-least-once** while the Agent is connected or via offline outbox drain after reconnect.
2. **Effects:** **Exactly-once mutation effect** per `operation_id` via Agent idempotency (SR-IDEM-01/02).
3. **Results:** Agent → Cloud result delivery is **at-least-once until Cloud acknowledgment**. Agent durably stores results and retries (WSS and/or HTTPS). Cloud applies results idempotently by `operation_id` (§5.6.1).
4. **Not claimed:** transport-level exactly-once without idempotency keys.

### Offline queue, revoke, ordering, backpressure

1. Cloud persists outbound commands in PostgreSQL outbox (modular monolith — no separate broker service).
2. **Depth + TTL** required (SR-QUEUE-LIMIT). V1 defaults (tunable): max **1000** pending mutating commands per Agent; mutating command TTL **24h**; overflow **fail closed** (reject new mutates; do not silently drop in-flight without marking failed/unknown appropriately).
3. On Agent credential revoke, role revoke, or Action disable: queued mutates for that scope are **cancelled** and must not execute (SR-AGENT-ROT-02 / SR-QUEUE-REAUTH).
4. **Ordering:** per-Agent **best-effort FIFO** for dispatch; **no cross-connection total order guarantee**. Mutating operations remain correct via `operation_id` idempotency, not global ordering.
5. **Backpressure:** if outbox depth exceeds soft threshold, Cloud surfaces Agent unhealthy / degraded and slows accept of new mutates; hard limit fails closed.

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
- **Negative:** Must handle WS reconnect, proxy timeouts, and dual-path result ack (WSS + HTTPS); dual crypto (TLS + Ed25519 envelopes) is intentional defense in depth.
- **Follow-ups:** Security review of this ADR; domain model (#6) for outbox/result/agent key tables; Backend/Agent implementation after gates; Platform TLS and reverse-proxy WebSocket config.

## Related documents

- [agent-cloud-protocol-v1.md](../agent-cloud-protocol-v1.md) — flows, failure modes, AC, handoffs
- ADR 0002, ADR 0006, Issue #2 architecture on `main`
