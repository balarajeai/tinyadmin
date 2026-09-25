package protocol

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"testing"
)

func TestValidateMutatingCommand_MissingDigest(t *testing.T) {
	cmd := &Command{
		CommandType: CommandTypeExecute,
		Authorization: &AuthorizationEnvelope{
			MutationPayloadSHA256: "",
			MaxAffectedRecords:    1,
		},
		Payload: json.RawMessage(`{}`),
	}
	
	err := ValidateMutatingCommand(cmd, "")
	if err == nil {
		t.Error("expected error for missing mutation_payload_sha256")
	}
}

func TestValidateMutatingCommand_TamperedPayload(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "action",
			"action_definition_id": "unlock-user",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	
	canonical, _ := CanonicalizeJSON(payload)
	digest := ComputeDigestBytes(canonical)
	
	tamperedPayload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "action",
			"action_definition_id": "unlock-user",
		},
		Targets:            map[string]any{"userId": "456"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	tamperedBytes, _ := json.Marshal(tamperedPayload)
	
	cmd := &Command{
		CommandType: CommandTypeExecute,
		Authorization: &AuthorizationEnvelope{
			MutationPayloadSHA256: digest,
			MaxAffectedRecords:    1,
		},
		Payload: tamperedBytes,
	}
	
	err := ValidateMutatingCommand(cmd, "")
	if err == nil {
		t.Error("expected error for tampered payload")
	}
}

func TestValidateMutatingCommand_WrongActionType(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type": "approved_field",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	
	payloadBytes, _ := json.Marshal(payload)
	canonical, _ := CanonicalizeJSON(payload)
	digest := ComputeDigestBytes(canonical)
	
	cmd := &Command{
		CommandType: CommandTypeExecute,
		Authorization: &AuthorizationEnvelope{
			MutationPayloadSHA256: digest,
			MaxAffectedRecords:    1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateMutatingCommand(cmd, "")
	if err == nil {
		t.Error("expected error for non-action type")
	}
}

func TestValidateMutatingCommand_MaxAffectedMismatch(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "action",
			"action_definition_id": "unlock-user",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 10,
	}
	
	payloadBytes, _ := json.Marshal(payload)
	canonical, _ := json.Marshal(payload)
	digest := ComputeDigestBytes(canonical)
	
	cmd := &Command{
		CommandType: CommandTypeExecute,
		Authorization: &AuthorizationEnvelope{
			MutationPayloadSHA256: digest,
			MaxAffectedRecords:    1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateMutatingCommand(cmd, "")
	if err == nil {
		t.Error("expected error for max_affected_records mismatch")
	}
}

func TestValidateMutatingCommand_Success(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "action",
			"action_definition_id": "unlock-user",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	
	payloadBytes, _ := json.Marshal(payload)
	canonical, _ := CanonicalizeJSON(payload)
	digest := ComputeDigestBytes(canonical)
	
	cmd := &Command{
		CommandType: CommandTypeExecute,
		Authorization: &AuthorizationEnvelope{
			MutationPayloadSHA256: digest,
			MaxAffectedRecords:    1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateMutatingCommand(cmd, "unlock-user")
	if err != nil {
		t.Errorf("expected no error, got: %v", err)
	}
}

func ComputeDigestBytes(canonical []byte) string {
	hash := sha256.Sum256(canonical)
	return hex.EncodeToString(hash[:])
}
