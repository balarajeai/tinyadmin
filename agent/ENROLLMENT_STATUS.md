# Agent Implementation Status for Issue #19

## Enrollment / Outbound Identity Status

### IMPLEMENTED AGENT-SIDE
- **Agent Ed25519 keypair generation and storage** (`internal/identity`)
  - Generate, load, save Ed25519 keys
  - Sign messages with Agent private key
  - Verify Cloud signatures on incoming commands
- **Cloud command signature verification** (`internal/protocol`)
  - Verify Ed25519 signatures on authorization envelopes using base64url encoding
  - Validate payload digest (SHA-256 over RFC 8785 JCS canonical mutation payload)
  - Action binding validation (envelope and payload action_or_field_op consistency)
  - Fail-closed on tampering, mismatched org/env/agent/connection, expired envelopes
- **Outbound WSS client** (`internal/cloud`)
  - Dial Cloud WSS endpoint over TLS (no inbound Agent port)
  - Reconnect with exponential backoff
  - Send heartbeats
  - Receive commands, result_ack, cancel/revoke sync responses
  - Mutex-protected WebSocket write serialization
- **Challenge-response session authentication** (`internal/cloud/client.go`)
  - Receive `challenge` messages from Cloud
  - Sign challenge nonces with Agent Ed25519 private key
  - Send `challenge_response` messages to Cloud
  - Receive `session_ok` or `session_failed` responses
  - **Session authentication gate**: Reject commands, cancel/revoke sync, and result_ack until session is authenticated
- **Cancel/revoke synchronization on reconnect** (`internal/cloud/client.go`)
  - Send `cancel_revoke_sync_request` message after session authenticated
  - Receive `cancel_revoke_sync_response` with authoritative revoked operation IDs
  - Notify operation manager to transition revoked operations to `unknown` state
  - Full bidirectional sync implemented agent-side
- **Authenticated result_ack handling** (`internal/cloud/client.go`)
  - Receive `result_ack` messages from Cloud
  - Validate session is authenticated before processing ack
  - Notify storage layer to mark results as acknowledged
  - Fail-closed: ignore result_ack if session not authenticated
- **Durable result retry** (`internal/operation/manager.go`, `internal/storage`)
  - SQLite WAL mode with busy_timeout for concurrency
  - Persist results to storage before WSS send
  - Retry unacked results until Cloud sends authenticated `result_ack`
  - Crash recovery: transition in-flight operations to `unknown` state on startup
- **Mandatory mutation payload digest** (`internal/protocol/messages.go`)
  - Enforce `mutation_payload_sha256` presence for all mutating commands (execute)
  - Compute canonical JSON digest using RFC 8785 JCS (`github.com/gowebpki/jcs`)
  - Reject commands with missing or mismatched digest
- **Action binding enforcement** (`internal/protocol/messages.go`, `internal/operation/manager.go`)
  - Validate action_or_field_op.type == "action" for preview and execute
  - Validate action_definition_id matches expected Unlock User Action ID
  - Enforce envelope and payload action binding consistency
  - Reject preview and execute for unauthorized or mismatched actions

### CLOUD DEPENDENCY (requires Cloud Issue #18)
- **Cloud challenge issuance**
  - Cloud WSS gateway must send `challenge` messages with fresh nonces
  - Cloud must implement challenge verification and session establishment
- **Cloud session_ok/session_failed responses**
  - Cloud must verify Agent's challenge_response signature
  - Cloud must send `session_ok` or `session_failed` to complete authentication
- **Authoritative cancel/revoke response**
  - Cloud must implement `cancel_revoke_sync_response` with authoritative revoked operation list
  - Cloud must persist and track operation revocations
- **Cloud authenticated result_ack**
  - Cloud must send `result_ack` messages for durable result delivery
  - Cloud must persist Agent results
- **Cloud WSS endpoint for E2E**
  - Cloud must deploy WSS gateway accepting Agent connections
  - Cloud must implement command signing service with Ed25519

### DEFERRED (not required for Issue #19 vertical slice)
- **HTTPS enrollment flow**
  - Full one-time token exchange with Cloud `POST /agent/v1/activate`
  - Agent stores Cloud command-signing public key from enrollment response
  - Cloud stores Agent public key
  - Owned by Cloud backend Issue #18
- **HTTPS result POST fallback**
  - Protocol document specifies `POST /agent/v1/results` for non-WSS delivery
  - Agent currently sends results only via WSS
  - Will add HTTPS fallback when Cloud endpoint is ready
- **Key rotation**
  - Agent supports signing/verification primitives
  - Full rotation protocol and Agent-signed outbound message expansion deferred

## Issue #3 Compliance Status

| Requirement | Agent Status | Cloud Status | Notes |
| --- | --- | --- | --- |
| Agent-initiated outbound only | ✅ COMPLETE | ⏳ REQUIRED | WSS client dials Cloud; no inbound port |
| TLS on all communication | ✅ COMPLETE | ⏳ REQUIRED | Strict wss:// enforcement; HTTPS enrollment deferred |
| Ed25519 Agent identity | ✅ COMPLETE | ⏳ REQUIRED | Keypair generation, storage, signing, verification |
| Cloud-signed command envelopes | ✅ COMPLETE | ⏳ REQUIRED | Signature + digest verification, fail-closed |
| Durable result delivery | ✅ COMPLETE | ⏳ REQUIRED | SQLite unacked results, retry until authenticated ack |
| Operation state machine | ✅ COMPLETE | N/A | received→authorized→executing→succeeded/failed/unknown |
| Idempotency | ✅ COMPLETE | N/A | SQLite deduplication, operation_id enforcement |
| Cancel/revoke before execution | ✅ COMPLETE | ⏳ REQUIRED | Agent sync implemented; awaits Cloud authoritative response |
| Challenge-response session auth | ✅ COMPLETE | ⏳ REQUIRED | Agent challenge-response and session gate implemented; awaits Cloud challenge issuance |
| HTTPS result fallback | 🔲 DEFERRED | 🔲 DEFERRED | Not required for vertical slice |
| Full enrollment flow | 🔲 DEFERRED | 🔲 DEFERRED | Owned by Cloud #18 |

## Ready For
- **Code Review**: YES (Agent-side protocol implementation complete)
- **QA**: YES (unit tests pass, integration tests use real PostgreSQL when available)
- **Security Review**: YES (all blocking security requirements implemented agent-side)
- **Cloud #18 Integration**: READY (Agent can receive and verify Cloud-signed commands; challenge-response ready)

## Not Ready For
- **Production deployment**: NO (requires Cloud #18 backend implementation)
- **End-to-end Issue #3 compliance**: NO (requires Cloud #18 WSS gateway, command signing, challenge issuance, result_ack)
