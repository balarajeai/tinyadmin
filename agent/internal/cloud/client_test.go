package cloud

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/gorilla/websocket"

	"github.com/balarajeai/tinyadmin/agent/internal/protocol"
	"github.com/balarajeai/tinyadmin/agent/internal/storage"
)

func testPrivateKey(t *testing.T) ed25519.PrivateKey {
	_, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate test key: %v", err)
	}
	return priv
}

func TestCloudClient_ResultAckClearsDurableState(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	result := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-1",
		Status:          "succeeded",
		ProtocolVersion: 1,
	}

	resultData, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("failed to marshal result: %v", err)
	}

	if err := store.RecordUnackedResult("op-1", resultData); err != nil {
		t.Fatalf("failed to record unacked result: %v", err)
	}

	unacked, err := store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results: %v", err)
	}
	if len(unacked) != 1 {
		t.Fatalf("expected 1 unacked result, got %d", len(unacked))
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	
	client := &Client{
		store:  store,
		logger: logger,
	}
	
	client.sessionAuthMu.Lock()
	client.sessionAuth = true
	client.sessionAuthMu.Unlock()

	ackMsg := map[string]any{
		"operation_id": "op-1",
		"acknowledged": true,
	}
	ackBytes, err := json.Marshal(ackMsg)
	if err != nil {
		t.Fatalf("failed to marshal ack: %v", err)
	}

	if err := client.handleResultAck(ackBytes); err != nil {
		t.Fatalf("handleResultAck failed: %v", err)
	}

	unackedAfter, err := store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results after ack: %v", err)
	}
	if len(unackedAfter) != 0 {
		t.Errorf("expected 0 unacked results after ack, got %d", len(unackedAfter))
	}
}

func TestCloudClient_DuplicateAckSafe(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	result := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-1",
		Status:          "succeeded",
		ProtocolVersion: 1,
	}

	resultData, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("failed to marshal result: %v", err)
	}

	if err := store.RecordUnackedResult("op-1", resultData); err != nil {
		t.Fatalf("failed to record unacked result: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	
	client := &Client{
		store:  store,
		logger: logger,
	}
	
	client.sessionAuthMu.Lock()
	client.sessionAuth = true
	client.sessionAuthMu.Unlock()

	ackMsg := map[string]any{
		"operation_id": "op-1",
		"acknowledged": true,
	}
	ackBytes, err := json.Marshal(ackMsg)
	if err != nil {
		t.Fatalf("failed to marshal ack: %v", err)
	}

	if err := client.handleResultAck(ackBytes); err != nil {
		t.Fatalf("first handleResultAck failed: %v", err)
	}

	if err := client.handleResultAck(ackBytes); err != nil {
		t.Fatalf("duplicate handleResultAck failed: %v", err)
	}

	unacked, err := store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results: %v", err)
	}
	if len(unacked) != 0 {
		t.Errorf("expected 0 unacked results after duplicate ack, got %d", len(unacked))
	}
}

func TestCloudClient_UnknownAckDoesNotCorruptState(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	result := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-1",
		Status:          "succeeded",
		ProtocolVersion: 1,
	}

	resultData, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("failed to marshal result: %v", err)
	}

	if err := store.RecordUnackedResult("op-1", resultData); err != nil {
		t.Fatalf("failed to record unacked result: %v", err)
	}

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	
	client := &Client{
		store:  store,
		logger: logger,
	}
	
	client.sessionAuthMu.Lock()
	client.sessionAuth = true
	client.sessionAuthMu.Unlock()

	ackMsg := map[string]any{
		"operation_id": "op-nonexistent",
		"acknowledged": true,
	}
	ackBytes, err := json.Marshal(ackMsg)
	if err != nil {
		t.Fatalf("failed to marshal ack: %v", err)
	}

	if err := client.handleResultAck(ackBytes); err != nil {
		t.Fatalf("handleResultAck for nonexistent op failed: %v", err)
	}

	unacked, err := store.GetUnackedResults()
	if err != nil {
		t.Fatalf("failed to get unacked results: %v", err)
	}
	if len(unacked) != 1 {
		t.Errorf("expected 1 unacked result still present, got %d", len(unacked))
	}
}

func TestCloudClient_SendPendingResults(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	result1 := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-1",
		Status:          "succeeded",
		ProtocolVersion: 1,
	}
	resultData1, _ := json.Marshal(result1)
	store.RecordUnackedResult("op-1", resultData1)

	result2 := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     "op-2",
		Status:          "failed",
		ProtocolVersion: 1,
	}
	resultData2, _ := json.Marshal(result2)
	store.RecordUnackedResult("op-2", resultData2)

	resultsCh := make(chan protocol.ResultMessage, 2)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("upgrade failed: %v", err)
			return
		}
		defer conn.Close()

		for {
			var result protocol.ResultMessage
			if err := conn.ReadJSON(&result); err != nil {
				return
			}
			resultsCh <- result
		}
	}))
	defer server.Close()

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	
	client := NewClient(
		"ws://"+server.Listener.Addr().String(),
		"agent-1",
		testPrivateKey(t),
		1*time.Second,
		10*time.Second,
		30*time.Second,
		store,
		logger,
	)

	if err := client.connect(); err != nil {
		t.Fatalf("failed to connect: %v", err)
	}
	defer client.Stop()

	if err := client.sendPendingResults(); err != nil {
		t.Fatalf("sendPendingResults failed: %v", err)
	}

	timeout := time.After(1 * time.Second)
	receivedCount := 0
	
	for receivedCount < 2 {
		select {
		case <-resultsCh:
			receivedCount++
		case <-timeout:
			t.Fatalf("timeout waiting for results, received %d", receivedCount)
		}
	}

	if receivedCount != 2 {
		t.Errorf("expected 2 results sent, got %d", receivedCount)
	}
}

func TestCloudClient_WakeSignalNonBlocking(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := storage.NewStore(tmpDir + "/test.db")
	if err != nil {
		t.Fatalf("failed to create store: %v", err)
	}
	defer store.Close()

	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	
	client := NewClient(
		"ws://localhost:9999",
		"agent-1",
		testPrivateKey(t),
		1*time.Second,
		10*time.Second,
		30*time.Second,
		store,
		logger,
	)

	for i := 0; i < 100; i++ {
		client.WakeResultSender()
	}

	select {
	case <-client.resultWake:
	default:
		t.Error("wake channel should have at least one signal")
	}

	select {
	case <-client.resultWake:
		t.Error("wake channel should not have multiple buffered signals")
	default:
	}
}
