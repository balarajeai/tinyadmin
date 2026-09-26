# TinyAdmin Agent

Production-quality Go Agent implementing PostgreSQL Unlock User vertical slice per Issue #19.

## Architecture

The Agent is structured following best practices with clear separation of concerns:

```
agent/
├── cmd/tinyadmin-agent/       # Main entry point
├── internal/
│   ├── config/                # Configuration validation
│   ├── identity/              # Ed25519 keypair management
│   ├── cloud/                 # WebSocket Cloud client with TLS
│   ├── protocol/              # Message types, JCS canonicalization, signature verification
│   ├── connection/            # Connection allowlist resolution
│   ├── postgres/              # PostgreSQL client and operations
│   ├── action/                # Unlock User action implementation
│   ├── storage/               # SQLite durable state management
│   └── operation/             # Operation lifecycle and command handling
```

## Security Properties

This implementation satisfies the following security requirements from Issue #5:

- **SR-AGENT-001**: Ed25519 identity, single-use enrollment tokens
- **SR-AGENT-002**: Per-agent keypairs, private keys never sent to Cloud
- **SR-AGENT-003**: Immutable org/environment binding, cancel/revoke sync before execution
- **SR-CMD-001**: Signature verification, payload digest validation, binding checks
- **SR-CMD-002**: Idempotent operation handling, no blind retry on indeterminate outcome
- **SR-ACTION-001**: Parameterized SQL only, no arbitrary SQL execution
- **SR-ACTION-002**: Max affected records enforcement
- **SR-PREVIEW-001**: Preview never mutates database
- **SR-SECRETS-001**: Customer DB credentials never sent to Cloud
- **SR-EXEC-001**: Never fabricate success, `unknown` when indeterminate

## Configuration

Example `config.yaml`:

```yaml
agent:
  id: agent-1
  organization_id: org-1
  environment_id: env-1
  private_key_path: /var/secrets/agent-key.hex

cloud:
  endpoint: wss://cloud.tinyadmin.example.com/agent/v1
  command_signing_key: <hex-encoded-ed25519-public-key>
  reconnect_base_delay: 1s
  reconnect_max_delay: 60s
  heartbeat_interval: 30s
  clock_skew_tolerance: 5m

connections:
  - id: conn-1
    organization_id: org-1
    environment_id: env-1
    secret_ref: POSTGRES_DSN_CONN1

storage:
  path: /var/lib/tinyadmin-agent/agent.db
```

### Connection Secrets

Database credentials are resolved from environment variables referenced by `secret_ref`:

```bash
export POSTGRES_DSN_CONN1="postgres://appuser:password@localhost:5432/mydb?sslmode=require"
```

**Important**: Customer DB credentials are never sent to Cloud. The Agent resolves them locally using the opaque `secret_ref` identifier.

## Testing

### Unit Tests

Run unit tests (no external dependencies required):

```bash
go test ./internal/config ./internal/protocol ./internal/storage -v
```

All unit tests pass:
- Config validation (SR-AGENT-003, SR-SECRETS-001)
- Signature verification (SR-CMD-001)
- Payload digest validation (SR-CMD-001)
- Envelope validation with binding checks (SR-AGENT-003, SR-CMD-001)
- Operation idempotency (SR-CMD-002)
- Durable state management

### Integration Tests

PostgreSQL integration tests require a running PostgreSQL instance. They will skip gracefully if PostgreSQL is not available.

**With Docker:**

```bash
docker run -d \
  --name tinyadmin-test-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  postgres:16-alpine

export TEST_POSTGRES_DSN="postgres://postgres:postgres@localhost:5432/testdb?sslmode=disable"
go test ./internal/postgres -v
```

Integration tests verify:
- **SR-PREVIEW-001**: Preview does not mutate database (checksum verified)
- **SR-ACTION-002**: Max affected records enforcement
- **SR-CMD-002**: Duplicate execution prevented
- Transaction rollback behavior
- Schema discovery
- Search functionality
- Connection failure handling

### Race Detection

```bash
go test -race ./...
```

### All Tests

```bash
go test ./... -v
```

## Test Coverage Map

| Test | Security Requirement | Evidence |
|------|---------------------|----------|
| `TestConfigValidation/connection_org_mismatch` | SR-AGENT-003 | Rejects connection with mismatched org |
| `TestConfigValidation/connection_env_mismatch` | SR-AGENT-003 | Rejects connection with mismatched env |
| `TestValidateEnvelope/org_mismatch` | SR-CMD-001 | Rejects envelope with wrong org |
| `TestValidateEnvelope/env_mismatch` | SR-CMD-001 | Rejects envelope with wrong env |
| `TestValidateEnvelope/agent_mismatch` | SR-CMD-001 | Rejects envelope with wrong agent |
| `TestValidateEnvelope/expired_envelope` | SR-CMD-001 | Rejects expired envelope |
| `TestVerifyEnvelopeSignature` | SR-CMD-001 | Rejects tampered signature |
| `TestVerifyPayloadDigest` | SR-CMD-001 | Rejects mismatched payload digest |
| `TestOperationIdempotency` | SR-CMD-002 | Duplicate operation_id doesn't overwrite |
| `TestPostgresIntegration_PreviewNoMutation` | SR-PREVIEW-001 | Preview leaves DB unchanged |
| `TestPostgresIntegration_ExecuteAffectedRowsLimit` | SR-ACTION-002 | Enforces max affected records |
| `TestPostgresIntegration_DuplicateExecution` | SR-CMD-002 | Second execute fails (no double mutation) |

## Building

```bash
go build -o tinyadmin-agent ./cmd/tinyadmin-agent
```

## Running

```bash
export POSTGRES_DSN_CONN1="postgres://..."
./tinyadmin-agent -config config.yaml
```

## Protocol Conformance

This implementation conforms to:
- `docs/architecture/agent-cloud-protocol-v1.md` (Issue #3)
- `docs/architecture/adr/0007-agent-cloud-protocol-wss.md`
- `docs/security/v1-security-requirements.md` (Issue #5)
- `docs/qa/v1-verification-strategy.md` (Issue #7)

### Key Protocol Features

1. **Outbound-only connection**: Agent initiates WSS to Cloud (P-OUTBOUND)
2. **Ed25519 identity**: Per-agent keypair (P-AGENT-ID)
3. **Signed commands**: Cloud-signed authorization envelopes (P-CMD-AUTH)
4. **Binding validation**: Org/env/agent/connection/action verified (P-ORG-BIND, P-ENV-BIND)
5. **Replay resistance**: Operation_id deduplication + expiry (P-REPLAY)
6. **Durable results**: Retry until authenticated ack (§5.6.1)
7. **Fail closed**: Invalid/expired/mismatched commands rejected

## Operation Lifecycle

```
received → authorized → execution_intent_persisted → executing → succeeded/failed/unknown
```

Terminal states are persisted durably. `unknown` is used when outcome is indeterminate (never fabricate success).

## Limitations

- **MongoDB**: Out of scope for this issue (PostgreSQL only)
- **Cloud backend**: Requires backend Issue #18 for full end-to-end testing
- **Multiple actions**: Only Unlock User implemented
- **Rollback**: Not implemented in this slice

## Dependencies

- `github.com/gorilla/websocket` - WebSocket client for Cloud connection
- `github.com/lib/pq` - PostgreSQL driver
- `github.com/mattn/go-sqlite3` - SQLite for durable storage
- `gopkg.in/yaml.v3` - YAML configuration parsing
- Go 1.21+

## License

See repository LICENSE.
