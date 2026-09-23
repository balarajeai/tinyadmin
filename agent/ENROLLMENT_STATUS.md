# Agent Implementation Status for Issue #19

## Enrollment / Outbound Identity Status

### IMPLEMENTED NOW
- **Agent Ed25519 keypair generation and storage** (`internal/identity`)
  - Generate, load, save Ed25519 keys
  - Sign messages with Agent private key (for future Agent→Cloud signed requests)
  - Verify Cloud signatures on incoming commands
- **Cloud command signature verification** (`internal/protocol`)
  - Verify Ed25519 signatures on authorization envelopes
  - Validate payload digest (SHA-256 over JCS canonical mutation payload)
  - Fail-closed on tampering, mismatched org/env/agent/connection, expired envelopes
- **Outbound WSS client** (`internal/cloud`)
  - Dial Cloud WSS endpoint over TLS (no inbound Agent port)
  - Reconnect with exponential backoff
  - Send heartbeats
  - Receive commands, result_ack, cancel/revoke sync

### STUBBED (Cloud #18 dependency)
- **Challenge-response session authentication**
  - Protocol structure exists in docs
  - Agent WSS client connects but does not implement challenge-response handshake yet
  - Cloud backend (#18) will implement challenge issuance and verification
  - Agent will add challenge-response in future iteration when Cloud is ready
- **Cancel/revoke synchronization on reconnect**
  - `syncCancelRevoke()` method exists in `internal/cloud/client.go`
  - Sends `cancel_revoke_sync_request` message
  - Calls registered cancel/revoke handler
  - Full bidirectional sync semantics depend on Cloud #18 implementation

### DEFERRED (not required for Issue #19 vertical slice)
- **HTTPS enrollment flow**
  - Full one-time token exchange with Cloud `/agent/v1/activate`
  - Agent stores Cloud command-signing public key from enrollment response
  - Cloud stores Agent public key
  - Owned by Cloud backend Issue #18
- **HTTPS result POST fallback**
  - Protocol document specifies `POST /agent/v1/results` for non-WSS delivery
  - Agent currently sends results only via WSS
  - Will add HTTPS fallback when Cloud endpoint is ready
- **Agent-signed outbound messages** (rotation, result delivery auth)
  - Agent can sign (identity package supports it)
  - Protocol structure exists
  - Full end-to-end flow deferred until Cloud #18 defines exact message formats

## Issue #3 Compliance Status

| Requirement | Status | Evidence |
| --- | --- | --- |
| Agent-initiated outbound only | ✅ COMPLETE | WSS client dials Cloud; no inbound port |
| TLS on all communication | ✅ COMPLETE | WSS uses TLS; HTTPS enrollment deferred to #18 |
| Ed25519 Agent identity | ✅ COMPLETE | Keypair generation, storage, signing, verification |
| Cloud-signed command envelopes | ✅ COMPLETE | Signature + digest verification, fail-closed |
| Durable result delivery | ✅ COMPLETE | SQLite unacked results, retry until authenticated ack |
| Operation state machine | ✅ COMPLETE | received→authorized→executing→succeeded/failed/unknown |
| Idempotency | ✅ COMPLETE | SQLite deduplication, operation_id enforcement |
| Cancel/revoke before execution | ⚠️ STUBBED | Method exists, awaits Cloud #18 full sync |
| Challenge-response session auth | ⚠️ STUBBED | Awaits Cloud #18 challenge issuance |
| HTTPS result fallback | 🔲 DEFERRED | Not required for vertical slice |
| Full enrollment flow | 🔲 DEFERRED | Owned by Cloud #18 |

## Ready For
- **Code Review**: YES (Agent-side protocol implementation complete)
- **QA**: YES (unit tests pass, integration tests skip gracefully when PostgreSQL unavailable)
- **Security Review**: YES (all blocking security requirements implemented; challenge-response stub documented)
- **Cloud #18 Integration**: READY (Agent can receive and verify Cloud-signed commands when #18 implements signing)

## Not Ready For
- **Production deployment**: NO (requires Cloud #18 backend implementation)
- **End-to-end testing**: NO (requires Cloud #18 WSS gateway and command signing)
