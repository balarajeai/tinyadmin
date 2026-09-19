# ADR 0006: Mandatory Cloud↔Agent security properties (Issue #3 constraints)

- **Status:** Proposed
- **Date:** 2026-09-19
- **Related issues:** #2 (governing), #3 (protocol selection), #4 / #5 (Security)
- **Remediates:** `SEC-PR10-001`, supports `SEC-PR10-002` / `SEC-PR10-003`

## Context

PR #10 originally deferred Agent↔Cloud transport/authn/delivery to Issue #3 without a normative property checklist. Security review **SEC-PR10-001** (BLOCKING HIGH) requires architecture to state mandatory properties so #3 cannot choose a weak protocol by default.

## Decision

Issue #3 **MUST** produce a protocol ADR that satisfies **all** of the following properties. This ADR does **not** select mTLS, signed JWT, or any other specific mechanism.

| ID | Property | Requirement |
| --- | --- | --- |
| P-OUTBOUND | Outbound-only | Agent initiates Cloud connectivity; no customer inbound Agent control ports required |
| P-ENCRYPT | Encrypted transport | All control traffic encrypted (TLS or equivalent) |
| P-AGENT-ID | Authenticated Agent identity | Cloud authenticates a specific enrolled Agent; org-wide static secret alone is insufficient |
| P-CMD-AUTH | Command authenticity/integrity | Agent can detect forged or tampered commands |
| P-REPLAY | Replay resistance | Sensitive/mutating commands resist replay (nonce/jti/sequence/expiry or equivalent) |
| P-ORG-BIND | Organization binding | Enrollment, session, and sensitive commands bound to one org; Agent rejects mismatch |
| P-ENV-BIND | Environment binding | Enrollment/activation and sensitive commands bound to one env; Agent rejects mismatch |

Additionally (architecture §3.4–§3.5, not alternative protocol choices):

- V1 Agent ↔ exactly one org + one env
- Mutating commands carry Cloud-issued authorization bindings (operation id, actor, org, env, agent, connection, Action/field op, expiry); Agent rejects invalid/expired/mismatched bindings
- Encoding of those bindings is owned by Issue #3

## Alternatives considered

1. **Leave properties entirely to Security requirements docs**
   - Rejected for Issue #2 DoD: architecture acceptance criteria must be enforceable by implementers/Code Review; Security SRs remain authoritative for security gates, architecture mirrors normative properties.
2. **Select mTLS (or JWT) in this ADR**
   - Rejected: owned by Issue #3; premature mechanism choice.
3. **Property checklist without Agent reject duties**
   - Rejected: properties without Agent enforcement invite confused-deputy failures (SEC-PR10-003).

## Consequences

- **Positive:** Clear conformance checklist for #3; reduces insecure-by-default risk.
- **Negative:** #3 scope includes proving each property; may constrain some simpler designs.
- **Follow-ups:** #3 ADR must include a conformance table mapping each P-* to the chosen mechanism. Security has **acknowledged** Issue #2 trust-boundary inputs (SEC-PR10-001..004 CLEARED on `0eab8da`); this ADR remains a constraint for Issue #3 and does **not** approve #3 mechanisms. Independent Code Review of PR #10 is a separate gate.

## Related issues

- #2, #3, #4, #5
- Security findings SEC-PR10-001..003
