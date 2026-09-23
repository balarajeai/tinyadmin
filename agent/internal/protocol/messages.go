package protocol

import (
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"time"
)

const ProtocolVersion = 1

type MessageType string

const (
	MessageTypeCommand MessageType = "command"
	MessageTypeResult  MessageType = "result"
)

type CommandType string

const (
	CommandTypeDiscover CommandType = "discover"
	CommandTypeSearch   CommandType = "search"
	CommandTypePreview  CommandType = "preview"
	CommandTypeExecute  CommandType = "execute"
	CommandTypeRollback CommandType = "rollback"
)

type Command struct {
	MessageType       MessageType            `json:"message_type"`
	CommandType       CommandType            `json:"command_type"`
	Authorization     *AuthorizationEnvelope `json:"authorization"`
	Payload           json.RawMessage        `json:"payload,omitempty"`
	ProtocolVersion   int                    `json:"protocol_version"`
}

type AuthorizationEnvelope struct {
	Kid                    string    `json:"kid"`
	OperationID            string    `json:"operation_id"`
	ActorID                string    `json:"actor_id"`
	OrganizationID         string    `json:"organization_id"`
	EnvironmentID          string    `json:"environment_id"`
	AgentID                string    `json:"agent_id"`
	ConnectionID           string    `json:"connection_id"`
	ActionOrFieldOp        any       `json:"action_or_field_op"`
	MutationPayloadSHA256  string    `json:"mutation_payload_sha256,omitempty"`
	MaxAffectedRecords     int       `json:"max_affected_records,omitempty"`
	IssuedAt               time.Time `json:"iat"`
	ExpiresAt              time.Time `json:"exp"`
	Signature              string    `json:"signature"`
}

type MutationPayload struct {
	ActionOrFieldOp    any         `json:"action_or_field_op"`
	Targets            any         `json:"targets"`
	Parameters         any         `json:"parameters"`
	MaxAffectedRecords int         `json:"max_affected_records"`
}

type ActionOperation struct {
	Type               string `json:"type"`
	ActionDefinitionID string `json:"action_definition_id"`
}

type ResultMessage struct {
	MessageType     MessageType `json:"message_type"`
	OperationID     string      `json:"operation_id"`
	Status          string      `json:"status"`
	Result          any         `json:"result,omitempty"`
	ErrorMessage    string      `json:"error_message,omitempty"`
	ProtocolVersion int         `json:"protocol_version"`
}

type ResultAck struct {
	OperationID string `json:"operation_id"`
	Acknowledged bool  `json:"acknowledged"`
}

func CanonicalizeJSON(data any) ([]byte, error) {
	b, err := json.Marshal(data)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal: %w", err)
	}

	var normalized any
	if err := json.Unmarshal(b, &normalized); err != nil {
		return nil, fmt.Errorf("failed to unmarshal: %w", err)
	}

	canonical, err := json.Marshal(normalized)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal canonical: %w", err)
	}

	return canonical, nil
}

func ComputePayloadDigest(payload *MutationPayload) (string, error) {
	canonical, err := CanonicalizeJSON(payload)
	if err != nil {
		return "", fmt.Errorf("failed to canonicalize payload: %w", err)
	}

	hash := sha256.Sum256(canonical)
	return hex.EncodeToString(hash[:]), nil
}

func VerifyEnvelopeSignature(envelope *AuthorizationEnvelope, cloudPublicKey ed25519.PublicKey) error {
	if envelope.Signature == "" {
		return errors.New("missing signature")
	}

	signature, err := hex.DecodeString(envelope.Signature)
	if err != nil {
		return fmt.Errorf("failed to decode signature: %w", err)
	}

	envelopeCopy := *envelope
	envelopeCopy.Signature = ""

	canonical, err := CanonicalizeJSON(envelopeCopy)
	if err != nil {
		return fmt.Errorf("failed to canonicalize envelope: %w", err)
	}

	if !ed25519.Verify(cloudPublicKey, canonical, signature) {
		return errors.New("invalid signature")
	}

	return nil
}

func VerifyPayloadDigest(payload *MutationPayload, expectedDigest string) error {
	computed, err := ComputePayloadDigest(payload)
	if err != nil {
		return fmt.Errorf("failed to compute digest: %w", err)
	}

	if computed != expectedDigest {
		return fmt.Errorf("payload digest mismatch: expected %s, got %s", expectedDigest, computed)
	}

	return nil
}

func ValidateEnvelope(envelope *AuthorizationEnvelope, agentOrgID, agentEnvID, agentID string, cloudPublicKey ed25519.PublicKey, clockSkewTolerance time.Duration) error {
	if err := VerifyEnvelopeSignature(envelope, cloudPublicKey); err != nil {
		return fmt.Errorf("signature verification failed: %w", err)
	}

	if envelope.OrganizationID != agentOrgID {
		return fmt.Errorf("organization mismatch: expected %s, got %s", agentOrgID, envelope.OrganizationID)
	}

	if envelope.EnvironmentID != agentEnvID {
		return fmt.Errorf("environment mismatch: expected %s, got %s", agentEnvID, envelope.EnvironmentID)
	}

	if envelope.AgentID != agentID {
		return fmt.Errorf("agent mismatch: expected %s, got %s", agentID, envelope.AgentID)
	}

	now := time.Now()
	if now.Before(envelope.IssuedAt.Add(-clockSkewTolerance)) {
		return fmt.Errorf("envelope not yet valid (iat: %v, now: %v)", envelope.IssuedAt, now)
	}

	if now.After(envelope.ExpiresAt.Add(clockSkewTolerance)) {
		return fmt.Errorf("envelope expired (exp: %v, now: %v)", envelope.ExpiresAt, now)
	}

	return nil
}
