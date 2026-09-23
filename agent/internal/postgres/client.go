package postgres

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	_ "github.com/lib/pq"
)

type Client struct {
	db *sql.DB
}

func NewClient(dsn string) (*Client, error) {
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	return &Client{db: db}, nil
}

func (c *Client) Close() error {
	if c.db != nil {
		return c.db.Close()
	}
	return nil
}

type TableInfo struct {
	Schema  string
	Name    string
	Columns []ColumnInfo
}

type ColumnInfo struct {
	Name     string
	DataType string
	IsNullable bool
}

func (c *Client) DiscoverSchema(ctx context.Context) ([]TableInfo, error) {
	query := `
		SELECT 
			table_schema, 
			table_name,
			column_name,
			data_type,
			is_nullable = 'YES' as is_nullable
		FROM information_schema.columns
		WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
		ORDER BY table_schema, table_name, ordinal_position
	`

	rows, err := c.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to query schema: %w", err)
	}
	defer rows.Close()

	tablesMap := make(map[string]*TableInfo)
	
	for rows.Next() {
		var schema, tableName, columnName, dataType string
		var isNullable bool

		if err := rows.Scan(&schema, &tableName, &columnName, &dataType, &isNullable); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		key := fmt.Sprintf("%s.%s", schema, tableName)
		table, exists := tablesMap[key]
		if !exists {
			table = &TableInfo{
				Schema:  schema,
				Name:    tableName,
				Columns: []ColumnInfo{},
			}
			tablesMap[key] = table
		}

		table.Columns = append(table.Columns, ColumnInfo{
			Name:       columnName,
			DataType:   dataType,
			IsNullable: isNullable,
		})
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating rows: %w", err)
	}

	tables := make([]TableInfo, 0, len(tablesMap))
	for _, table := range tablesMap{
		tables = append(tables, *table)
	}

	return tables, nil
}

type SearchResult struct {
	Rows []map[string]any
}

func (c *Client) SearchUsers(ctx context.Context, email string) (*SearchResult, error) {
	query := `
		SELECT id, email, locked, status, failed_login_count, updated_at
		FROM users
		WHERE email ILIKE $1
		LIMIT 100
	`

	rows, err := c.db.QueryContext(ctx, query, "%"+email+"%")
	if err != nil {
		return nil, fmt.Errorf("failed to search users: %w", err)
	}
	defer rows.Close()

	results := &SearchResult{Rows: []map[string]any{}}

	cols, err := rows.Columns()
	if err != nil {
		return nil, fmt.Errorf("failed to get columns: %w", err)
	}

	for rows.Next() {
		values := make([]any, len(cols))
		valuePtrs := make([]any, len(cols))
		for i := range values {
			valuePtrs[i] = &values[i]
		}

		if err := rows.Scan(valuePtrs...); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		row := make(map[string]any)
		for i, col := range cols {
			row[col] = values[i]
		}
		results.Rows = append(results.Rows, row)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating rows: %w", err)
	}

	return results, nil
}

type PreviewResult struct {
	BeforeState  map[string]any
	AfterState   map[string]any
	WouldAffect  int
}

func (c *Client) PreviewUnlockUser(ctx context.Context, userID string) (*PreviewResult, error) {
	tx, err := c.db.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	query := `SELECT id, email, locked, status FROM users WHERE id = $1 AND locked = true`
	
	var id, email, status string
	var locked bool

	err = tx.QueryRowContext(ctx, query).Scan(&id, &email, &locked, &status)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, fmt.Errorf("user %s not found or not locked", userID)
		}
		return nil, fmt.Errorf("failed to query user: %w", err)
	}

	beforeState := map[string]any{
		"id":     id,
		"email":  email,
		"locked": locked,
		"status": status,
	}

	afterState := map[string]any{
		"id":     id,
		"email":  email,
		"locked": false,
		"status": status,
	}

	return &PreviewResult{
		BeforeState: beforeState,
		AfterState:  afterState,
		WouldAffect: 1,
	}, nil
}

type ExecuteResult struct {
	AffectedRows int
	BeforeState  map[string]any
	AfterState   map[string]any
}

func (c *Client) ExecuteUnlockUser(ctx context.Context, userID string, maxAffected int) (*ExecuteResult, error) {
	tx, err := c.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	queryBefore := `SELECT id, email, locked, status FROM users WHERE id = $1 FOR UPDATE`
	
	var id, email, status string
	var locked bool

	err = tx.QueryRowContext(ctx, queryBefore).Scan(&id, &email, &locked, &status)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, fmt.Errorf("user %s not found", userID)
		}
		return nil, fmt.Errorf("failed to query user: %w", err)
	}

	beforeState := map[string]any{
		"id":     id,
		"email":  email,
		"locked": locked,
		"status": status,
	}

	updateQuery := `UPDATE users SET locked = false, updated_at = NOW() WHERE id = $1 AND locked = true`
	result, err := tx.ExecContext(ctx, updateQuery, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to update user: %w", err)
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return nil, fmt.Errorf("failed to get affected rows: %w", err)
	}

	if affected > int64(maxAffected) {
		return nil, fmt.Errorf("affected rows (%d) exceeds maximum allowed (%d)", affected, maxAffected)
	}

	if affected == 0 {
		return nil, errors.New("no rows affected (user may not be locked)")
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	afterState := map[string]any{
		"id":     id,
		"email":  email,
		"locked": false,
		"status": status,
	}

	return &ExecuteResult{
		AffectedRows: int(affected),
		BeforeState:  beforeState,
		AfterState:   afterState,
	}, nil
}
