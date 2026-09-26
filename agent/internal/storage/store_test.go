package storage

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestStoreInitialization(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test.db")

	store, err := NewStore(dbPath)
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	if _, err := os.Stat(dbPath); os.IsNotExist(err) {
		t.Error("database file was not created")
	}
}

func TestRecordAndGetOperation(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	op := &Operation{
		OperationID: "op-1",
		State:       StateReceived,
		CommandData: json.RawMessage(`{"test":"data"}`),
	}

	if err := store.RecordOperation(op); err != nil {
		t.Fatalf("failed to record operation: %v", err)
	}

	retrieved, err := store.GetOperation("op-1")
	if err != nil {
		t.Fatalf("failed to get operation: %v", err)
	}

	if retrieved == nil {
		t.Fatal("operation not found")
	}

	if retrieved.OperationID != "op-1" {
		t.Errorf("expected operation_id=op-1, got %s", retrieved.OperationID)
	}
	if retrieved.State != StateReceived {
		t.Errorf("expected state=received, got %s", retrieved.State)
	}
}

func TestOperationIdempotency(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	op := &Operation{
		OperationID: "op-1",
		State:       StateReceived,
		CommandData: json.RawMessage(`{"test":"data"}`),
	}

	if err := store.RecordOperation(op); err != nil {
		t.Fatalf("failed to record operation: %v", err)
	}

	op2 := &Operation{
		OperationID: "op-1",
		State:       StateExecuting,
		CommandData: json.RawMessage(`{"different":"data"}`),
	}

	if err := store.RecordOperation(op2); err != nil {
		t.Fatalf("failed to record duplicate operation: %v", err)
	}

	retrieved, err := store.GetOperation("op-1")
	if err != nil {
		t.Fatalf("failed to get operation: %v", err)
	}

	if retrieved.State != StateReceived {
		t.Errorf("duplicate insert should not overwrite, expected state=received, got %s", retrieved.State)
	}
}

func TestUpdateOperationState(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	op := &Operation{
		OperationID: "op-1",
		State:       StateReceived,
	}

	if err := store.RecordOperation(op); err != nil {
		t.Fatalf("failed to record operation: %v", err)
	}

	resultData := json.RawMessage(`{"status":"success"}`)
	if err := store.UpdateOperationState("op-1", StateSucceeded, resultData); err != nil {
		t.Fatalf("failed to update state: %v", err)
	}

	retrieved, err := store.GetOperation("op-1")
	if err != nil {
		t.Fatalf("failed to get operation: %v", err)
	}

	if retrieved.State != StateSucceeded {
		t.Errorf("expected state=succeeded, got %s", retrieved.State)
	}

	if string(retrieved.ResultData) != string(resultData) {
		t.Errorf("result data mismatch")
	}
}

func TestUnackedResults(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	resultData := json.RawMessage(`{"status":"success"}`)

	if err := store.RecordUnackedResult("op-1", resultData); err != nil {
		t.Fatalf("failed to record unacked result: %v", err)
	}

	results, err := store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results: %v", err)
	}

	if len(results) != 1 {
		t.Fatalf("expected 1 unacked result, got %d", len(results))
	}

	if results[0].OperationID != "op-1" {
		t.Errorf("expected operation_id=op-1, got %s", results[0].OperationID)
	}

	if err := store.DeleteUnackedResult("op-1"); err != nil {
		t.Fatalf("failed to delete unacked result: %v", err)
	}

	results, err = store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results after delete: %v", err)
	}

	if len(results) != 0 {
		t.Errorf("expected 0 unacked results after delete, got %d", len(results))
	}
}

func TestCancelledOperations(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	cancelled, err := store.IsCancelled("op-1")
	if err != nil {
		t.Fatalf("failed to check cancelled: %v", err)
	}
	if cancelled {
		t.Error("operation should not be cancelled initially")
	}

	if err := store.RecordCancelledOperation("op-1"); err != nil {
		t.Fatalf("failed to record cancelled: %v", err)
	}

	cancelled, err = store.IsCancelled("op-1")
	if err != nil {
		t.Fatalf("failed to check cancelled: %v", err)
	}
	if !cancelled {
		t.Error("operation should be cancelled")
	}
}

func TestOperationLifecycle(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	op := &Operation{
		OperationID: "op-lifecycle",
		State:       StateReceived,
		CommandData: json.RawMessage(`{"cmd":"test"}`),
	}

	if err := store.RecordOperation(op); err != nil {
		t.Fatalf("failed to record: %v", err)
	}

	states := []OperationState{
		StateAuthorized,
		StateExecutionIntentPersisted,
		StateExecuting,
		StateSucceeded,
	}

	for _, state := range states {
		time.Sleep(10 * time.Millisecond)
		
		if err := store.UpdateOperationState("op-lifecycle", state, nil); err != nil {
			t.Fatalf("failed to update to %s: %v", state, err)
		}

		retrieved, err := store.GetOperation("op-lifecycle")
		if err != nil {
			t.Fatalf("failed to get operation: %v", err)
		}

		if retrieved.State != state {
			t.Errorf("expected state=%s, got %s", state, retrieved.State)
		}
	}
}
