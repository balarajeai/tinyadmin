package cloud

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"math/rand"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"

	"github.com/balarajeai/tinyadmin/agent/internal/protocol"
	"github.com/balarajeai/tinyadmin/agent/internal/storage"
)

type Client struct {
	endpoint           string
	agentID            string
	reconnectBaseDelay time.Duration
	reconnectMaxDelay  time.Duration
	heartbeatInterval  time.Duration
	logger             *slog.Logger

	conn        *websocket.Conn
	connMu      sync.Mutex
	
	commandHandler func(context.Context, *protocol.Command) (*protocol.ResultMessage, error)
	cancelRevokeHandler func([]string) error
	
	resultQueue chan *protocol.ResultMessage
	
	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

func NewClient(
	endpoint string,
	agentID string,
	reconnectBaseDelay, reconnectMaxDelay, heartbeatInterval time.Duration,
	logger *slog.Logger,
) *Client {
	ctx, cancel := context.WithCancel(context.Background())
	
	return &Client{
		endpoint:           endpoint,
		agentID:            agentID,
		reconnectBaseDelay: reconnectBaseDelay,
		reconnectMaxDelay:  reconnectMaxDelay,
		heartbeatInterval:  heartbeatInterval,
		logger:             logger,
		resultQueue:        make(chan *protocol.ResultMessage, 100),
		ctx:                ctx,
		cancel:             cancel,
	}
}

func (c *Client) SetCommandHandler(handler func(context.Context, *protocol.Command) (*protocol.ResultMessage, error)) {
	c.commandHandler = handler
}

func (c *Client) SetCancelRevokeHandler(handler func([]string) error) {
	c.cancelRevokeHandler = handler
}

func (c *Client) Start() error {
	c.wg.Add(1)
	go c.connectionLoop()
	return nil
}

func (c *Client) Stop() error {
	c.cancel()
	c.wg.Wait()
	
	c.connMu.Lock()
	if c.conn != nil {
		c.conn.Close()
	}
	c.connMu.Unlock()
	
	return nil
}

func (c *Client) QueueResult(result *protocol.ResultMessage) {
	select {
	case c.resultQueue <- result:
	case <-c.ctx.Done():
	default:
		c.logger.Warn("result queue full, dropping result", "operation_id", result.OperationID)
	}
}

func (c *Client) connectionLoop() {
	defer c.wg.Done()
	
	attemptCount := 0
	
	for {
		select {
		case <-c.ctx.Done():
			return
		default:
		}
		
		if attemptCount > 0 {
			delay := c.calculateBackoff(attemptCount)
			c.logger.Info("reconnecting", "attempt", attemptCount, "delay", delay)
			
			select {
			case <-time.After(delay):
			case <-c.ctx.Done():
				return
			}
		}
		
		err := c.connect()
		if err != nil {
			c.logger.Error("connection failed", "error", err, "attempt", attemptCount)
			attemptCount++
			continue
		}
		
		attemptCount = 0
		
		err = c.handleConnection()
		if err != nil {
			c.logger.Error("connection error", "error", err)
		}
		
		c.connMu.Lock()
		if c.conn != nil {
			c.conn.Close()
			c.conn = nil
		}
		c.connMu.Unlock()
	}
}

func (c *Client) calculateBackoff(attempt int) time.Duration {
	if attempt <= 0 {
		return 0
	}
	
	multiplier := math.Pow(2, float64(attempt-1))
	delay := time.Duration(float64(c.reconnectBaseDelay) * multiplier)
	
	jitter := time.Duration(rand.Float64() * float64(delay) * 0.1)
	delay += jitter
	
	if delay > c.reconnectMaxDelay {
		delay = c.reconnectMaxDelay
	}
	
	return delay
}

func (c *Client) connect() error {
	dialer := websocket.Dialer{
		TLSClientConfig: &tls.Config{
			MinVersion: tls.VersionTLS12,
		},
		HandshakeTimeout: 10 * time.Second,
	}
	
	headers := http.Header{}
	headers.Set("X-Agent-ID", c.agentID)
	
	conn, _, err := dialer.Dial(c.endpoint, headers)
	if err != nil {
		return fmt.Errorf("failed to dial: %w", err)
	}
	
	c.connMu.Lock()
	c.conn = conn
	c.connMu.Unlock()
	
	c.logger.Info("connected to cloud", "endpoint", c.endpoint)
	
	return nil
}

func (c *Client) handleConnection() error {
	ctx, cancel := context.WithCancel(c.ctx)
	defer cancel()
	
	if err := c.syncCancelRevoke(); err != nil {
		return fmt.Errorf("cancel/revoke sync failed: %w", err)
	}
	
	c.wg.Add(2)
	
	errChan := make(chan error, 2)
	
	go func() {
		defer c.wg.Done()
		errChan <- c.receiveLoop(ctx)
	}()
	
	go func() {
		defer c.wg.Done()
		errChan <- c.heartbeatLoop(ctx)
	}()
	
	err := <-errChan
	cancel()
	
	return err
}

func (c *Client) syncCancelRevoke() error {
	c.logger.Info("syncing cancel/revoke state")
	
	msg := map[string]any{
		"message_type": "cancel_revoke_sync_request",
		"agent_id":     c.agentID,
	}
	
	c.connMu.Lock()
	conn := c.conn
	c.connMu.Unlock()
	
	if conn == nil {
		return errors.New("no connection")
	}
	
	if err := conn.WriteJSON(msg); err != nil {
		return fmt.Errorf("failed to send sync request: %w", err)
	}
	
	conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	defer conn.SetReadDeadline(time.Time{})
	
	var syncMsg struct {
		MessageType        string   `json:"message_type"`
		CancelledOperations []string `json:"cancelled_operations"`
	}
	
	if err := conn.ReadJSON(&syncMsg); err != nil {
		return fmt.Errorf("failed to read sync response: %w", err)
	}
	
	if syncMsg.MessageType != "cancel_revoke_sync_response" {
		return fmt.Errorf("unexpected message type: %s", syncMsg.MessageType)
	}
	
	if c.cancelRevokeHandler != nil && len(syncMsg.CancelledOperations) > 0 {
		if err := c.cancelRevokeHandler(syncMsg.CancelledOperations); err != nil {
			return fmt.Errorf("failed to process cancel/revoke: %w", err)
		}
	}
	
	c.logger.Info("cancel/revoke sync complete", "cancelled_count", len(syncMsg.CancelledOperations))
	
	return nil
}

func (c *Client) receiveLoop(ctx context.Context) error {
	c.connMu.Lock()
	conn := c.conn
	c.connMu.Unlock()
	
	if conn == nil {
		return errors.New("no connection")
	}
	
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		
		_, msgBytes, err := conn.ReadMessage()
		if err != nil {
			return fmt.Errorf("read error: %w", err)
		}
		
		var msgType struct {
			MessageType string `json:"message_type"`
		}
		
		if err := json.Unmarshal(msgBytes, &msgType); err != nil {
			c.logger.Error("failed to parse message type", "error", err)
			continue
		}
		
		switch msgType.MessageType {
		case "command":
			if err := c.handleCommandMessage(ctx, msgBytes); err != nil {
				c.logger.Error("failed to handle command", "error", err)
			}
			
		case "result_ack":
			if err := c.handleResultAck(msgBytes); err != nil {
				c.logger.Error("failed to handle result_ack", "error", err)
			}
			
		case "ping":
			if err := c.sendPong(); err != nil {
				c.logger.Error("failed to send pong", "error", err)
			}
			
		default:
			c.logger.Warn("unknown message type", "type", msgType.MessageType)
		}
	}
}

func (c *Client) handleCommandMessage(ctx context.Context, msgBytes []byte) error {
	var cmd protocol.Command
	if err := json.Unmarshal(msgBytes, &cmd); err != nil {
		return fmt.Errorf("failed to unmarshal command: %w", err)
	}
	
	c.logger.Info("received command", 
		"type", cmd.CommandType,
		"operation_id", cmd.Authorization.OperationID)
	
	if c.commandHandler == nil {
		return errors.New("no command handler registered")
	}
	
	result, err := c.commandHandler(ctx, &cmd)
	if err != nil {
		c.logger.Error("command handler error", "error", err)
		return err
	}
	
	if result != nil {
		c.QueueResult(result)
	}
	
	return nil
}

func (c *Client) handleResultAck(msgBytes []byte) error {
	var ack protocol.ResultAck
	if err := json.Unmarshal(msgBytes, &ack); err != nil {
		return fmt.Errorf("failed to unmarshal result_ack: %w", err)
	}
	
	c.logger.Info("received result_ack", "operation_id", ack.OperationID)
	
	return nil
}

func (c *Client) sendPong() error {
	msg := map[string]string{
		"message_type": "pong",
	}
	
	c.connMu.Lock()
	conn := c.conn
	c.connMu.Unlock()
	
	if conn == nil {
		return errors.New("no connection")
	}
	
	return conn.WriteJSON(msg)
}

func (c *Client) heartbeatLoop(ctx context.Context) error {
	ticker := time.NewTicker(c.heartbeatInterval)
	defer ticker.Stop()
	
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			if err := c.sendHeartbeat(); err != nil {
				return fmt.Errorf("heartbeat failed: %w", err)
			}
		case result := <-c.resultQueue:
			if err := c.sendResult(result); err != nil {
				c.logger.Error("failed to send result", "error", err, "operation_id", result.OperationID)
				c.QueueResult(result)
			}
		}
	}
}

func (c *Client) sendHeartbeat() error {
	msg := map[string]string{
		"message_type": "heartbeat",
		"agent_id":     c.agentID,
	}
	
	c.connMu.Lock()
	conn := c.conn
	c.connMu.Unlock()
	
	if conn == nil {
		return errors.New("no connection")
	}
	
	return conn.WriteJSON(msg)
}

func (c *Client) sendResult(result *protocol.ResultMessage) error {
	c.connMu.Lock()
	conn := c.conn
	c.connMu.Unlock()
	
	if conn == nil {
		return errors.New("no connection")
	}
	
	if err := conn.WriteJSON(result); err != nil {
		return fmt.Errorf("failed to send result: %w", err)
	}
	
	c.logger.Info("sent result", "operation_id", result.OperationID, "status", result.Status)
	
	return nil
}

func (c *Client) RetryUnackedResults(store *storage.Store) error {
	unacked, err := store.GetUnackedResults()
	if err != nil {
		return fmt.Errorf("failed to get unacked results: %w", err)
	}
	
	for _, ur := range unacked {
		var result protocol.ResultMessage
		if err := json.Unmarshal(ur.ResultData, &result); err != nil {
			c.logger.Error("failed to unmarshal unacked result", "operation_id", ur.OperationID, "error", err)
			continue
		}
		
		c.logger.Info("retrying unacked result", "operation_id", ur.OperationID)
		c.QueueResult(&result)
	}
	
	return nil
}
