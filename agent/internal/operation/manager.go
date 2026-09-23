package operation

import (
	"context"
	"crypto/ed25519"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/balarajeai/tinyadmin/agent/internal/action"
	"github.com/balarajeai/tinyadmin/agent/internal/connection"
	"github.com/balarajeai/tinyadmin/agent/internal/postgres"
	"github.com/balarajeai/tinyadmin/agent/internal/protocol"
	"github.com/balarajeai/tinyadmin/agent/internal/storage"
)

type Manager struct {
	store              *storage.Store
	connMgr            *connection.Manager
	agentID            string
	orgID              string
	envID              string
	cloudPublicKey     ed25519.PublicKey
	clockSkewTolerance time.Duration
	logger             *slog.Logger
}

func NewManager(
	store *storage.Store,
	connMgr *connection.Manager,
	agentID, orgID, envID string,
	cloudPublicKey ed25519.PublicKey,
	clockSkewTolerance time.Duration,
	logger *slog.Logger,
) *Manager {
	return &Manager{
		store:              store,
		connMgr:            connMgr,
		agentID:            agentID,
		orgID:              orgID,
		envID:              envID,
		cloudPublicKey:     cloudPublicKey,
		clockSkewTolerance: clockSkewTolerance,
		logger:             logger,
	}
}

func (m *Manager) HandleCommand(ctx context.Context, cmd *protocol.Command) (*protocol.ResultMessage, error) {
	if cmd.Authorization == nil {
		return m.buildErrorResult("", "missing authorization envelope"), nil
	}

	env := cmd.Authorization

	cancelled, err := m.store.IsCancelled(env.OperationID)
	if err != nil {
		return nil, fmt.Errorf("failed to check cancelled status: %w", err)
	}
	if cancelled {
		m.logger.Info("command rejected: operation cancelled", "operation_id", env.OperationID)
		return m.buildErrorResult(env.OperationID, "operation cancelled"), nil
	}

	existingOp, err := m.store.GetOperation(env.OperationID)
	if err != nil {
		return nil, fmt.Errorf("failed to get operation: %w", err)
	}

	if existingOp != nil {
		if existingOp.State == storage.StateSucceeded || existingOp.State == storage.StateFailed {
			m.logger.Info("returning cached result for duplicate operation", "operation_id", env.OperationID, "state", existingOp.State)
			
			var cachedResult protocol.ResultMessage
			if err := json.Unmarshal(existingOp.ResultData, &cachedResult); err != nil {
				return nil, fmt.Errorf("failed to unmarshal cached result: %w", err)
			}
			return &cachedResult, nil
		}

		if existingOp.State == storage.StateUnknown || existingOp.State == storage.StateExecuting {
			m.logger.Warn("duplicate command for indeterminate operation", "operation_id", env.OperationID, "state", existingOp.State)
			return m.buildErrorResult(env.OperationID, fmt.Sprintf("operation in indeterminate state: %s", existingOp.State)), nil
		}
	}

	commandData, err := json.Marshal(cmd)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal command: %w", err)
	}

	if existingOp == nil {
		if err := m.store.RecordOperation(&storage.Operation{
			OperationID: env.OperationID,
			State:       storage.StateReceived,
			CommandData: commandData,
		}); err != nil {
			return nil, fmt.Errorf("failed to record operation: %w", err)
		}
	}

	if err := protocol.ValidateEnvelope(env, m.orgID, m.envID, m.agentID, m.cloudPublicKey, m.clockSkewTolerance); err != nil {
		m.logger.Error("envelope validation failed", "operation_id", env.OperationID, "error", err)
		
		result := m.buildErrorResult(env.OperationID, fmt.Sprintf("envelope validation failed: %v", err))
		if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
			return nil, fmt.Errorf("failed to record validation failure: %w", err)
		}
		
		return result, nil
	}

	if err := m.store.UpdateOperationState(env.OperationID, storage.StateAuthorized, nil); err != nil {
		return nil, fmt.Errorf("failed to update to authorized state: %w", err)
	}

	dsn, err := m.connMgr.Resolve(env.ConnectionID)
	if err != nil {
		m.logger.Error("connection resolution failed", "operation_id", env.OperationID, "connection_id", env.ConnectionID, "error", err)
		
		result := m.buildErrorResult(env.OperationID, fmt.Sprintf("connection resolution failed: %v", err))
		if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
			return nil, fmt.Errorf("failed to record connection failure: %w", err)
		}
		
		return result, nil
	}

	switch cmd.CommandType {
	case protocol.CommandTypePreview:
		return m.handlePreview(ctx, env, cmd.Payload, dsn)
	case protocol.CommandTypeExecute:
		return m.handleExecute(ctx, env, cmd.Payload, dsn)
	default:
		return m.buildErrorResult(env.OperationID, fmt.Sprintf("unsupported command type: %s", cmd.CommandType)), nil
	}
}

func (m *Manager) handlePreview(ctx context.Context, env *protocol.AuthorizationEnvelope, payload json.RawMessage, dsn string) (*protocol.ResultMessage, error) {
	var mutPayload protocol.MutationPayload
	if err := json.Unmarshal(payload, &mutPayload); err != nil {
		return nil, fmt.Errorf("failed to unmarshal mutation payload: %w", err)
	}

	client, err := postgres.NewClient(dsn)
	if err != nil {
		result := m.buildErrorResult(env.OperationID, fmt.Sprintf("database connection failed: %v", err))
		if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
			return nil, fmt.Errorf("failed to record connection failure: %w", err)
		}
		return result, nil
	}
	defer client.Close()

	unlockAction := action.NewUnlockUserAction(client)
	previewResult, err := unlockAction.Preview(ctx, mutPayload.Targets)
	if err != nil {
		result := m.buildErrorResult(env.OperationID, fmt.Sprintf("preview failed: %v", err))
		if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
			return nil, fmt.Errorf("failed to record preview failure: %w", err)
		}
		return result, nil
	}

	result := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     env.OperationID,
		Status:          "preview_succeeded",
		Result:          previewResult,
		ProtocolVersion: protocol.ProtocolVersion,
	}

	if err := m.recordResult(env.OperationID, storage.StateSucceeded, result); err != nil {
		return nil, fmt.Errorf("failed to record preview result: %w", err)
	}

	return result, nil
}

func (m *Manager) handleExecute(ctx context.Context, env *protocol.AuthorizationEnvelope, payload json.RawMessage, dsn string) (*protocol.ResultMessage, error) {
	var mutPayload protocol.MutationPayload
	if err := json.Unmarshal(payload, &mutPayload); err != nil {
		return nil, fmt.Errorf("failed to unmarshal mutation payload: %w", err)
	}

	if env.MutationPayloadSHA256 != "" {
		if err := protocol.VerifyPayloadDigest(&mutPayload, env.MutationPayloadSHA256); err != nil {
			m.logger.Error("payload digest mismatch", "operation_id", env.OperationID, "error", err)
			
			result := m.buildErrorResult(env.OperationID, fmt.Sprintf("payload digest mismatch: %v", err))
			if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
				return nil, fmt.Errorf("failed to record digest mismatch: %w", err)
			}
			
			return result, nil
		}
	}

	if err := m.store.UpdateOperationState(env.OperationID, storage.StateExecutionIntentPersisted, nil); err != nil {
		return nil, fmt.Errorf("failed to persist execution intent: %w", err)
	}

	client, err := postgres.NewClient(dsn)
	if err != nil {
		result := m.buildErrorResult(env.OperationID, fmt.Sprintf("database connection failed: %v", err))
		if err := m.recordResult(env.OperationID, storage.StateFailed, result); err != nil {
			return nil, fmt.Errorf("failed to record connection failure: %w", err)
		}
		return result, nil
	}
	defer client.Close()

	if err := m.store.UpdateOperationState(env.OperationID, storage.StateExecuting, nil); err != nil {
		return nil, fmt.Errorf("failed to update to executing state: %w", err)
	}

	unlockAction := action.NewUnlockUserAction(client)
	execResult, err := unlockAction.Execute(ctx, mutPayload.Targets, mutPayload.MaxAffectedRecords)
	if err != nil {
		var resultMsg *protocol.ResultMessage
		if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
			resultMsg = &protocol.ResultMessage{
				MessageType:     protocol.MessageTypeResult,
				OperationID:     env.OperationID,
				Status:          "unknown",
				ErrorMessage:    fmt.Sprintf("execution uncertain: %v", err),
				ProtocolVersion: protocol.ProtocolVersion,
			}
			if err := m.recordResult(env.OperationID, storage.StateUnknown, resultMsg); err != nil {
				return nil, fmt.Errorf("failed to record unknown state: %w", err)
			}
		} else {
			resultMsg = m.buildErrorResult(env.OperationID, fmt.Sprintf("execution failed: %v", err))
			if err := m.recordResult(env.OperationID, storage.StateFailed, resultMsg); err != nil {
				return nil, fmt.Errorf("failed to record execution failure: %w", err)
			}
		}
		return resultMsg, nil
	}

	result := &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     env.OperationID,
		Status:          "succeeded",
		Result:          execResult,
		ProtocolVersion: protocol.ProtocolVersion,
	}

	if err := m.recordResult(env.OperationID, storage.StateSucceeded, result); err != nil {
		return nil, fmt.Errorf("failed to record success result: %w", err)
	}

	return result, nil
}

func (m *Manager) buildErrorResult(operationID, errorMsg string) *protocol.ResultMessage {
	return &protocol.ResultMessage{
		MessageType:     protocol.MessageTypeResult,
		OperationID:     operationID,
		Status:          "failed",
		ErrorMessage:    errorMsg,
		ProtocolVersion: protocol.ProtocolVersion,
	}
}

func (m *Manager) recordResult(operationID string, state storage.OperationState, result *protocol.ResultMessage) error {
	resultData, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("failed to marshal result: %w", err)
	}

	if err := m.store.UpdateOperationState(operationID, state, resultData); err != nil {
		return fmt.Errorf("failed to update operation state: %w", err)
	}

	if err := m.store.RecordUnackedResult(operationID, resultData); err != nil {
		return fmt.Errorf("failed to record unacked result: %w", err)
	}

	return nil
}

func (m *Manager) ProcessCancelRevoke(cancelledOps []string) error {
	for _, opID := range cancelledOps {
		if err := m.store.RecordCancelledOperation(opID); err != nil {
			return fmt.Errorf("failed to record cancelled operation %s: %w", opID, err)
		}
		m.logger.Info("recorded cancelled operation", "operation_id", opID)
	}
	return nil
}
