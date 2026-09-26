package protocol

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"testing"
	"time"
)

func TestCanonicalizeJSON(t *testing.T) {
	tests := []struct {
		name    string
		input   any
		wantErr bool
	}{
		{
			name: "simple object",
			input: map[string]any{
				"b": 2,
				"a": 1,
			},
			wantErr: false,
		},
		{
			name: "nested object",
			input: map[string]any{
				"action": map[string]string{
					"type": "unlock_user",
				},
				"targets": []string{"user-1"},
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := CanonicalizeJSON(tt.input)
			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
			} else {
				if err != nil {
					t.Fatalf("unexpected error: %v", err)
				}
				if len(result) == 0 {
					t.Fatal("expected non-empty result")
				}
			}
		})
	}
}

func TestComputePayloadDigest(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]string{
			"type":                 "action",
			"action_definition_id": "action-1",
		},
		Targets: map[string]string{
			"user_id": "user-1",
		},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}

	digest1, err := ComputePayloadDigest(payload)
	if err != nil {
		t.Fatalf("failed to compute digest: %v", err)
	}

	if len(digest1) != 64 {
		t.Errorf("expected 64-char hex string, got %d chars", len(digest1))
	}

	digest2, err := ComputePayloadDigest(payload)
	if err != nil {
		t.Fatalf("failed to compute digest: %v", err)
	}

	if digest1 != digest2 {
		t.Error("digest should be deterministic")
	}
}

func TestVerifyEnvelopeSignature(t *testing.T) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	envelope := &AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-1",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           time.Now(),
		ExpiresAt:          time.Now().Add(5 * time.Minute),
	}

	canonical, err := CanonicalizeJSON(envelope)
	if err != nil {
		t.Fatalf("failed to canonicalize: %v", err)
	}

	signature := ed25519.Sign(priv, canonical)
	envelope.Signature = base64.RawURLEncoding.EncodeToString(signature)

	if err := VerifyEnvelopeSignature(envelope, pub); err != nil {
		t.Errorf("valid signature rejected: %v", err)
	}

	tamperedSig := make([]byte, len(signature))
	copy(tamperedSig, signature)
	tamperedSig[len(tamperedSig)-1] ^= 0xFF
	envelope.Signature = base64.RawURLEncoding.EncodeToString(tamperedSig)
	if err := VerifyEnvelopeSignature(envelope, pub); err == nil {
		t.Error("tampered signature accepted")
	}
}

func TestValidateEnvelope(t *testing.T) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	now := time.Now()

	createEnvelope := func(orgID, envID, agentID string, iat, exp time.Time) *AuthorizationEnvelope {
		env := &AuthorizationEnvelope{
			Kid:            "key-1",
			OperationID:    "op-1",
			ActorID:        "actor-1",
			OrganizationID: orgID,
			EnvironmentID:  envID,
			AgentID:        agentID,
			ConnectionID:   "conn-1",
			ActionOrFieldOp: map[string]string{
				"type": "action",
			},
			MaxAffectedRecords: 1,
			IssuedAt:           iat,
			ExpiresAt:          exp,
		}

		canonical, _ := CanonicalizeJSON(env)
		signature := ed25519.Sign(priv, canonical)
		env.Signature = base64.RawURLEncoding.EncodeToString(signature)

		return env
	}

	t.Run("valid envelope", func(t *testing.T) {
		env := createEnvelope("org-1", "env-1", "agent-1", now.Add(-1*time.Minute), now.Add(5*time.Minute))
		if err := ValidateEnvelope(env, "org-1", "env-1", "agent-1", pub, 5*time.Minute); err != nil {
			t.Errorf("valid envelope rejected: %v", err)
		}
	})

	t.Run("org mismatch", func(t *testing.T) {
		env := createEnvelope("org-2", "env-1", "agent-1", now.Add(-1*time.Minute), now.Add(5*time.Minute))
		if err := ValidateEnvelope(env, "org-1", "env-1", "agent-1", pub, 5*time.Minute); err == nil {
			t.Error("org mismatch not detected")
		}
	})

	t.Run("env mismatch", func(t *testing.T) {
		env := createEnvelope("org-1", "env-2", "agent-1", now.Add(-1*time.Minute), now.Add(5*time.Minute))
		if err := ValidateEnvelope(env, "org-1", "env-1", "agent-1", pub, 5*time.Minute); err == nil {
			t.Error("env mismatch not detected")
		}
	})

	t.Run("agent mismatch", func(t *testing.T) {
		env := createEnvelope("org-1", "env-1", "agent-2", now.Add(-1*time.Minute), now.Add(5*time.Minute))
		if err := ValidateEnvelope(env, "org-1", "env-1", "agent-1", pub, 5*time.Minute); err == nil {
			t.Error("agent mismatch not detected")
		}
	})

	t.Run("expired envelope", func(t *testing.T) {
		env := createEnvelope("org-1", "env-1", "agent-1", now.Add(-10*time.Minute), now.Add(-5*time.Minute))
		if err := ValidateEnvelope(env, "org-1", "env-1", "agent-1", pub, 1*time.Minute); err == nil {
			t.Error("expired envelope not detected")
		}
	})
}

func TestVerifyPayloadDigest(t *testing.T) {
	payload := &MutationPayload{
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		Targets:            map[string]string{"user_id": "user-1"},
		Parameters:         map[string]any{},
		MaxAffectedRecords: 1,
	}

	correctDigest, err := ComputePayloadDigest(payload)
	if err != nil {
		t.Fatalf("failed to compute digest: %v", err)
	}

	if err := VerifyPayloadDigest(payload, correctDigest); err != nil {
		t.Errorf("correct digest rejected: %v", err)
	}

	wrongDigest := "0000000000000000000000000000000000000000000000000000000000000000"
	if err := VerifyPayloadDigest(payload, wrongDigest); err == nil {
		t.Error("wrong digest accepted")
	}
}
