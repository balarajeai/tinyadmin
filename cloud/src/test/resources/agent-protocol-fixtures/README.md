# Agent↔Cloud Protocol Contract Fixtures

Golden JSON examples for Issue #3 / ADR 0007 Agent↔Cloud protocol.

These fixtures demonstrate the exact message shapes Cloud and Agent must exchange.
Agent Engineer (Issue #19) can use these for independent verification.

## Message Types

- `session_hello.json` - Agent → Cloud: Initial authentication request (§5)
- `challenge.json` - Cloud → Agent: Challenge nonce for Ed25519 signature (§5)
- `result_ack.json` - Cloud → Agent: Authenticated acknowledgment of result (§7, SEC-PR12-005)
- `cancel_revoke_sync.json` - Cloud → Agent: Authoritative cancel/revoke state (§8, CR-PR12-001)

## Protocol Version

All messages include `protocol_version: 1` for V1.

## Canonicalization

Mutation payloads and ack payloads use RFC 8785 JCS canonicalization before digest/signature.

## Signature Encoding

- Ed25519 signatures: base64url-encoded (no padding), 64 bytes
- SHA-256 digests: lowercase hex, 64 characters

## Testing

Run `AgentProtocolContractTest` to verify message shapes and JCS consistency.
