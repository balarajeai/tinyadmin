package storage

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	_ "github.com/mattn/go-sqlite3"
)

type Store struct {
	db *sql.DB
}

func NewStore(path string) (*Store, error) {
	db, err := sql.Open("sqlite3", path)
	if err != nil {
		return nil, fmt.Errorf("failed to open storage: %w", err)
	}

	if _, err := db.Exec("PRAGMA journal_mode=WAL"); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to enable WAL mode: %w", err)
	}

	if _, err := db.Exec("PRAGMA busy_timeout=5000"); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to set busy_timeout: %w", err)
	}

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to ping storage: %w", err)
	}

	store := &Store{db: db}
	if err := store.initialize(); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to initialize storage: %w", err)
	}

	return store, nil
}

func (s *Store) Close() error {
	if s.db != nil {
		return s.db.Close()
	}
	return nil
}

func (s *Store) initialize() error {
	schema := `
		CREATE TABLE IF NOT EXISTS operations (
			operation_id TEXT PRIMARY KEY,
			state TEXT NOT NULL,
			command_data TEXT,
			result_data TEXT,
			created_at INTEGER NOT NULL,
			updated_at INTEGER NOT NULL
		);

		CREATE TABLE IF NOT EXISTS unacked_results (
			operation_id TEXT PRIMARY KEY,
			result_data TEXT NOT NULL,
			created_at INTEGER NOT NULL,
			FOREIGN KEY(operation_id) REFERENCES operations(operation_id)
		);

		CREATE TABLE IF NOT EXISTS cancelled_operations (
			operation_id TEXT PRIMARY KEY,
			cancelled_at INTEGER NOT NULL
		);
	`

	if _, err := s.db.Exec(schema); err != nil {
		return fmt.Errorf("failed to create schema: %w", err)
	}

	return nil
}

type OperationState string

const (
	StateReceived               OperationState = "received"
	StateAuthorized             OperationState = "authorized"
	StateExecutionIntentPersisted OperationState = "execution_intent_persisted"
	StateExecuting              OperationState = "executing"
	StateSucceeded              OperationState = "succeeded"
	StateFailed                 OperationState = "failed"
	StateUnknown                OperationState = "unknown"
)

type Operation struct {
	OperationID string
	State       OperationState
	CommandData json.RawMessage
	ResultData  json.RawMessage
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

func (s *Store) RecordOperation(op *Operation) error {
	now := time.Now().Unix()
	
	query := `
		INSERT OR IGNORE INTO operations (operation_id, state, command_data, result_data, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?)
	`
	
	_, err := s.db.Exec(query, op.OperationID, op.State, op.CommandData, op.ResultData, now, now)
	if err != nil {
		return fmt.Errorf("failed to record operation: %w", err)
	}

	return nil
}

func (s *Store) UpdateOperationState(operationID string, state OperationState, resultData json.RawMessage) error {
	now := time.Now().Unix()
	
	query := `UPDATE operations SET state = ?, result_data = ?, updated_at = ? WHERE operation_id = ?`
	
	result, err := s.db.Exec(query, state, resultData, now, operationID)
	if err != nil {
		return fmt.Errorf("failed to update operation state: %w", err)
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to check affected rows: %w", err)
	}

	if affected == 0 {
		return fmt.Errorf("operation %s not found", operationID)
	}

	return nil
}

func (s *Store) GetOperation(operationID string) (*Operation, error) {
	query := `SELECT operation_id, state, command_data, result_data, created_at, updated_at FROM operations WHERE operation_id = ?`
	
	row := s.db.QueryRow(query, operationID)
	
	var op Operation
	var createdAt, updatedAt int64
	var commandData, resultData sql.NullString

	err := row.Scan(&op.OperationID, &op.State, &commandData, &resultData, &createdAt, &updatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to get operation: %w", err)
	}

	if commandData.Valid {
		op.CommandData = json.RawMessage(commandData.String)
	}
	if resultData.Valid {
		op.ResultData = json.RawMessage(resultData.String)
	}

	op.CreatedAt = time.Unix(createdAt, 0)
	op.UpdatedAt = time.Unix(updatedAt, 0)

	return &op, nil
}

type UnackedResult struct {
	OperationID string
	ResultData  json.RawMessage
	CreatedAt   time.Time
}

func (s *Store) RecordUnackedResult(operationID string, resultData json.RawMessage) error {
	now := time.Now().Unix()
	
	query := `INSERT OR REPLACE INTO unacked_results (operation_id, result_data, created_at) VALUES (?, ?, ?)`
	
	_, err := s.db.Exec(query, operationID, resultData, now)
	if err != nil {
		return fmt.Errorf("failed to record unacked result: %w", err)
	}

	return nil
}

func (s *Store) DeleteUnackedResult(operationID string) error {
	query := `DELETE FROM unacked_results WHERE operation_id = ?`
	
	_, err := s.db.Exec(query, operationID)
	if err != nil {
		return fmt.Errorf("failed to delete unacked result: %w", err)
	}

	return nil
}

func (s *Store) GetUnackedResults() ([]UnackedResult, error) {
	query := `SELECT operation_id, result_data, created_at FROM unacked_results ORDER BY created_at ASC`
	
	rows, err := s.db.Query(query)
	if err != nil {
		return nil, fmt.Errorf("failed to query unacked results: %w", err)
	}
	defer rows.Close()

	var results []UnackedResult
	for rows.Next() {
		var r UnackedResult
		var createdAt int64

		if err := rows.Scan(&r.OperationID, &r.ResultData, &createdAt); err != nil {
			return nil, fmt.Errorf("failed to scan unacked result: %w", err)
		}

		r.CreatedAt = time.Unix(createdAt, 0)
		results = append(results, r)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating unacked results: %w", err)
	}

	return results, nil
}

func (s *Store) RecordCancelledOperation(operationID string) error {
	now := time.Now().Unix()
	
	query := `INSERT OR IGNORE INTO cancelled_operations (operation_id, cancelled_at) VALUES (?, ?)`
	
	_, err := s.db.Exec(query, operationID, now)
	if err != nil {
		return fmt.Errorf("failed to record cancelled operation: %w", err)
	}

	return nil
}

func (s *Store) IsCancelled(operationID string) (bool, error) {
	query := `SELECT 1 FROM cancelled_operations WHERE operation_id = ?`
	
	row := s.db.QueryRow(query, operationID)
	
	var exists int
	err := row.Scan(&exists)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, nil
		}
		return false, fmt.Errorf("failed to check cancelled status: %w", err)
	}

	return true, nil
}

func (s *Store) GetIndeterminateOperations() ([]*Operation, error) {
	query := `
		SELECT operation_id, state, command_data, result_data, created_at, updated_at
		FROM operations
		WHERE state IN (?, ?)
		ORDER BY created_at ASC
	`

	rows, err := s.db.Query(query, StateExecuting, StateExecutionIntentPersisted)
	if err != nil {
		return nil, fmt.Errorf("failed to query indeterminate operations: %w", err)
	}
	defer rows.Close()

	var ops []*Operation
	for rows.Next() {
		var op Operation
		var commandData, resultData sql.NullString
		var createdAt, updatedAt int64

		if err := rows.Scan(&op.OperationID, &op.State, &commandData, &resultData, &createdAt, &updatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan operation: %w", err)
		}

		if commandData.Valid {
			op.CommandData = json.RawMessage(commandData.String)
		}
		if resultData.Valid {
			op.ResultData = json.RawMessage(resultData.String)
		}

		op.CreatedAt = time.Unix(createdAt, 0)
		op.UpdatedAt = time.Unix(updatedAt, 0)

		ops = append(ops, &op)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating indeterminate operations: %w", err)
	}

	return ops, nil
}
