package action

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/balarajeai/tinyadmin/agent/internal/postgres"
)

type UnlockUserAction struct {
	client *postgres.Client
}

func NewUnlockUserAction(client *postgres.Client) *UnlockUserAction {
	return &UnlockUserAction{
		client: client,
	}
}

type UnlockUserTarget struct {
	UserID string `json:"user_id"`
}

type PreviewResponse struct {
	Before      map[string]any `json:"before"`
	After       map[string]any `json:"after"`
	WouldAffect int            `json:"would_affect"`
}

func (a *UnlockUserAction) Preview(ctx context.Context, targets any) (*PreviewResponse, error) {
	targetData, err := json.Marshal(targets)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal targets: %w", err)
	}

	var target UnlockUserTarget
	if err := json.Unmarshal(targetData, &target); err != nil {
		return nil, fmt.Errorf("failed to unmarshal targets: %w", err)
	}

	if target.UserID == "" {
		return nil, fmt.Errorf("user_id is required")
	}

	result, err := a.client.PreviewUnlockUser(ctx, target.UserID)
	if err != nil {
		return nil, fmt.Errorf("preview failed: %w", err)
	}

	return &PreviewResponse{
		Before:      result.BeforeState,
		After:       result.AfterState,
		WouldAffect: result.WouldAffect,
	}, nil
}

type ExecuteResponse struct {
	AffectedRows int            `json:"affected_rows"`
	Before       map[string]any `json:"before"`
	After        map[string]any `json:"after"`
}

func (a *UnlockUserAction) Execute(ctx context.Context, targets any, maxAffected int) (*ExecuteResponse, error) {
	targetData, err := json.Marshal(targets)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal targets: %w", err)
	}

	var target UnlockUserTarget
	if err := json.Unmarshal(targetData, &target); err != nil {
		return nil, fmt.Errorf("failed to unmarshal targets: %w", err)
	}

	if target.UserID == "" {
		return nil, fmt.Errorf("user_id is required")
	}

	result, err := a.client.ExecuteUnlockUser(ctx, target.UserID, maxAffected)
	if err != nil {
		return nil, fmt.Errorf("execute failed: %w", err)
	}

	return &ExecuteResponse{
		AffectedRows: result.AffectedRows,
		Before:       result.BeforeState,
		After:        result.AfterState,
	}, nil
}
