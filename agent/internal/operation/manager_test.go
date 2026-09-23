package operation

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/balarajeai/tinyadmin/agent/internal/connection"
	"github.com/balarajeai/tinyadmin/agent/internal/protocol"
	"github.com/balarajeai/tinyadmin/agent/internal/storage"
)

func TestOperationManager_DuplicateOperationReturnsCache(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		5*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-dup",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-1 * time.Minute),
		ExpiresAt:          now.Add(5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	firstResult := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-dup",
		Status:          "succeeded",
		ProtocolVersion: 1,
	}
	firstResultData, _ := json.Marshal(firstResult)
	
	store.RecordOperation(&storage.Operation{
		OperationID: "op-dup",
		State:       storage.StateSucceeded,
		ResultData:  firstResultData,
	})

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypePreview,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "succeeded" {
		t.Errorf("expected cached status 'succeeded', got %s", result.Status)
	}

	if result.OperationID != "op-dup" {
		t.Errorf("expected operation_id 'op-dup', got %s", result.OperationID)
	}
}

func TestOperationManager_WrongOrgRejected(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		5*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-wrong-org",
		ActorID:        "actor-1",
		OrganizationID: "org-2",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-1 * time.Minute),
		ExpiresAt:          now.Add(5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypePreview,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "failed" {
		t.Errorf("expected status 'failed' for wrong org, got %s", result.Status)
	}

	if result.ErrorMessage == "" {
		t.Error("expected error message for wrong org")
	}
}

func TestOperationManager_WrongEnvRejected(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		5*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-wrong-env",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-2",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-1 * time.Minute),
		ExpiresAt:          now.Add(5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypePreview,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "failed" {
		t.Errorf("expected status 'failed' for wrong env, got %s", result.Status)
	}

	if result.ErrorMessage == "" {
		t.Error("expected error message for wrong env")
	}
}

func TestOperationManager_ExpiredEnvelopeRejected(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		1*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-expired",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-10 * time.Minute),
		ExpiresAt:          now.Add(-5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypePreview,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "failed" {
		t.Errorf("expected status 'failed' for expired envelope, got %s", result.Status)
	}

	if result.ErrorMessage == "" {
		t.Error("expected error message for expired envelope")
	}
}

func TestOperationManager_CancelledOperationRejected(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	store.RecordCancelledOperation("op-cancelled")

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		5*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-cancelled",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-1 * time.Minute),
		ExpiresAt:          now.Add(5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypeExecute,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "failed" {
		t.Errorf("expected status 'failed' for cancelled operation, got %s", result.Status)
	}

	if result.ErrorMessage == "" {
		t.Error("expected error message for cancelled operation")
	}
}

func TestOperationManager_IndeterminateDuplicateRejected(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	store.RecordOperation(&storage.Operation{
		OperationID: "op-executing",
		State:       storage.StateExecuting,
	})

	connMgr := connection.NewManager(make(map[string]string))
	
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	mgr := NewManager(
		store,
		connMgr,
		"agent-1",
		"org-1",
		"env-1",
		pub,
		5*time.Minute,
		logger,
	)

	now := time.Now()
	env := &protocol.AuthorizationEnvelope{
		Kid:            "key-1",
		OperationID:    "op-executing",
		ActorID:        "actor-1",
		OrganizationID: "org-1",
		EnvironmentID:  "env-1",
		AgentID:        "agent-1",
		ConnectionID:   "conn-1",
		ActionOrFieldOp: map[string]string{
			"type": "action",
		},
		MaxAffectedRecords: 1,
		IssuedAt:           now.Add(-1 * time.Minute),
		ExpiresAt:          now.Add(5 * time.Minute),
	}

	canonical, _ := protocol.CanonicalizeJSON(env)
	signature := ed25519.Sign(priv, canonical)
	env.Signature = hex.EncodeToString(signature)

	cmd := &protocol.Command{
		MessageType:     protocol.MessageTypeCommand,
		CommandType:     protocol.CommandTypeExecute,
		Authorization:   env,
		ProtocolVersion: 1,
	}

	ctx := context.Background()
	result, err := mgr.HandleCommand(ctx, cmd)
	if err != nil {
		t.Fatalf("HandleCommand failed: %v", err)
	}

	if result.Status != "failed" {
		t.Errorf("expected status 'failed' for duplicate indeterminate operation, got %s", result.Status)
	}
}
