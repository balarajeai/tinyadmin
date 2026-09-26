package protocol

import (
	"encoding/json"
	"testing"
)

func TestValidateActionBinding_Success(t *testing.T) {
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
	
	cmd := &Command{
		CommandType: CommandTypePreview,
		Authorization: &AuthorizationEnvelope{
			ActionOrFieldOp: map[string]any{
				"type":                 "action",
				"action_definition_id": "unlock-user",
			},
			MaxAffectedRecords: 1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateActionBinding(cmd, "unlock-user")
	if err != nil {
		t.Errorf("expected no error, got: %v", err)
	}
}

func TestValidateActionBinding_WrongActionID(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "action",
			"action_definition_id": "delete-user",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	
	payloadBytes, _ := json.Marshal(payload)
	
	cmd := &Command{
		CommandType: CommandTypePreview,
		Authorization: &AuthorizationEnvelope{
			ActionOrFieldOp: map[string]any{
				"type":                 "action",
				"action_definition_id": "delete-user",
			},
			MaxAffectedRecords: 1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateActionBinding(cmd, "unlock-user")
	if err == nil {
		t.Error("expected error for wrong action_definition_id")
	}
}

func TestValidateActionBinding_WrongActionType(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]any{
			"type":                 "approved_field",
			"action_definition_id": "unlock-user",
		},
		Targets:            map[string]any{"userId": "123"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}
	
	payloadBytes, _ := json.Marshal(payload)
	
	cmd := &Command{
		CommandType: CommandTypePreview,
		Authorization: &AuthorizationEnvelope{
			ActionOrFieldOp: map[string]any{
				"type":                 "approved_field",
				"action_definition_id": "unlock-user",
			},
			MaxAffectedRecords: 1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateActionBinding(cmd, "unlock-user")
	if err == nil {
		t.Error("expected error for non-action type")
	}
}

func TestValidateActionBinding_MissingEnvelopeAction(t *testing.T) {
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
	
	cmd := &Command{
		CommandType: CommandTypePreview,
		Authorization: &AuthorizationEnvelope{
			ActionOrFieldOp: nil,
			MaxAffectedRecords: 1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateActionBinding(cmd, "unlock-user")
	if err == nil {
		t.Error("expected error for missing envelope action")
	}
}

func TestValidateActionBinding_EnvelopePayloadMismatch(t *testing.T) {
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
	
	cmd := &Command{
		CommandType: CommandTypePreview,
		Authorization: &AuthorizationEnvelope{
			ActionOrFieldOp: map[string]any{
				"type":                 "action",
				"action_definition_id": "delete-user",
			},
			MaxAffectedRecords: 1,
		},
		Payload: payloadBytes,
	}
	
	err := ValidateActionBinding(cmd, "")
	if err == nil {
		t.Error("expected error for envelope/payload action_definition_id mismatch")
	}
}
