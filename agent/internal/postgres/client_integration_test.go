package postgres

import (
	"context"
	"fmt"
	"os"
	"testing"
)

func getTestDSN() string {
	dsn := os.Getenv("TEST_POSTGRES_DSN")
	if dsn == "" {
		return "postgres://postgres:postgres@localhost:5432/testdb?sslmode=disable"
	}
	return dsn
}

func setupTestDB(t *testing.T) *Client {
	t.Helper()

	dsn := getTestDSN()
	client, err := NewClient(dsn)
	if err != nil {
		t.Skipf("PostgreSQL not available (set TEST_POSTGRES_DSN to run): %v", err)
	}

	createTableSQL := `
		DROP TABLE IF EXISTS users CASCADE;
		CREATE TABLE users (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			email TEXT NOT NULL UNIQUE,
			locked BOOLEAN NOT NULL DEFAULT FALSE,
			status TEXT NOT NULL DEFAULT 'active',
			failed_login_count INTEGER NOT NULL DEFAULT 0,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
		);
	`

	if _, err := client.db.Exec(createTableSQL); err != nil {
		t.Fatalf("failed to create test table: %v", err)
	}

	return client
}

func TestPostgresIntegration_PreviewNoMutation(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000001"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "locked@example.com", true, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	var lockedBefore bool
	err = client.db.QueryRowContext(ctx, "SELECT locked FROM users WHERE id = $1", userID).Scan(&lockedBefore)
	if err != nil {
		t.Fatalf("failed to query before state: %v", err)
	}

	if !lockedBefore {
		t.Fatal("user should be locked before preview")
	}

	_, err = client.PreviewUnlockUser(ctx, userID)
	if err != nil {
		t.Fatalf("preview failed: %v", err)
	}

	var lockedAfter bool
	err = client.db.QueryRowContext(ctx, "SELECT locked FROM users WHERE id = $1", userID).Scan(&lockedAfter)
	if err != nil {
		t.Fatalf("failed to query after state: %v", err)
	}

	if lockedAfter != lockedBefore {
		t.Error("SR-PREVIEW-001 FAILED: preview mutated the database")
	}
}

func TestPostgresIntegration_ExecuteUnlockSuccess(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000002"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "locked2@example.com", true, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	result, err := client.ExecuteUnlockUser(ctx, userID, 1)
	if err != nil {
		t.Fatalf("execute failed: %v", err)
	}

	if result.AffectedRows != 1 {
		t.Errorf("expected 1 affected row, got %d", result.AffectedRows)
	}

	if result.BeforeState["locked"] != true {
		t.Error("before state should show locked=true")
	}

	if result.AfterState["locked"] != false {
		t.Error("after state should show locked=false")
	}

	var lockedAfter bool
	err = client.db.QueryRowContext(ctx, "SELECT locked FROM users WHERE id = $1", userID).Scan(&lockedAfter)
	if err != nil {
		t.Fatalf("failed to verify final state: %v", err)
	}

	if lockedAfter {
		t.Error("user should be unlocked after execute")
	}
}

func TestPostgresIntegration_ExecuteAffectedRowsLimit(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000003"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "locked3@example.com", true, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	_, err = client.ExecuteUnlockUser(ctx, userID, 0)
	if err == nil {
		t.Error("SR-ACTION-002 FAILED: should reject when max_affected=0")
	}
}

func TestPostgresIntegration_ExecuteNoRowsAffected(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000004"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "unlocked@example.com", false, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	_, err = client.ExecuteUnlockUser(ctx, userID, 1)
	if err == nil {
		t.Error("should fail when no rows affected (user already unlocked)")
	}
}

func TestPostgresIntegration_SearchUsers(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	testUsers := []struct {
		id     string
		email  string
		locked bool
	}{
		{"00000000-0000-0000-0000-000000000010", "alice@example.com", true},
		{"00000000-0000-0000-0000-000000000011", "bob@example.com", false},
		{"00000000-0000-0000-0000-000000000012", "charlie@example.com", true},
	}

	for _, u := range testUsers {
		_, err := client.db.ExecContext(ctx, insertSQL, u.id, u.email, u.locked, "active")
		if err != nil {
			t.Fatalf("failed to insert test user: %v", err)
		}
	}

	result, err := client.SearchUsers(ctx, "example")
	if err != nil {
		t.Fatalf("search failed: %v", err)
	}

	if len(result.Rows) != 3 {
		t.Errorf("expected 3 results, got %d", len(result.Rows))
	}
}

func TestPostgresIntegration_DiscoverSchema(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	tables, err := client.DiscoverSchema(ctx)
	if err != nil {
		t.Fatalf("discover failed: %v", err)
	}

	found := false
	for _, table := range tables {
		if table.Name == "users" {
			found = true

			expectedColumns := []string{"id", "email", "locked", "status", "failed_login_count", "updated_at"}
			foundColumns := make(map[string]bool)
			for _, col := range table.Columns {
				foundColumns[col.Name] = true
			}

			for _, expected := range expectedColumns {
				if !foundColumns[expected] {
					t.Errorf("expected column %s not found", expected)
				}
			}
		}
	}

	if !found {
		t.Error("users table not discovered")
	}
}

func TestPostgresIntegration_TransactionRollback(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000020"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "txtest@example.com", true, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	tx, err := client.db.BeginTx(ctx, nil)
	if err != nil {
		t.Fatalf("failed to begin transaction: %v", err)
	}

	updateSQL := `UPDATE users SET locked = false WHERE id = $1`
	_, err = tx.ExecContext(ctx, updateSQL, userID)
	if err != nil {
		t.Fatalf("failed to update in transaction: %v", err)
	}

	if err := tx.Rollback(); err != nil {
		t.Fatalf("failed to rollback: %v", err)
	}

	var locked bool
	err = client.db.QueryRowContext(ctx, "SELECT locked FROM users WHERE id = $1", userID).Scan(&locked)
	if err != nil {
		t.Fatalf("failed to query after rollback: %v", err)
	}

	if !locked {
		t.Error("transaction rollback did not preserve locked state")
	}
}

func TestPostgresIntegration_DuplicateExecution(t *testing.T) {
	client := setupTestDB(t)
	defer client.Close()

	ctx := context.Background()

	insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
	userID := "00000000-0000-0000-0000-000000000030"
	_, err := client.db.ExecContext(ctx, insertSQL, userID, "dup@example.com", true, "active")
	if err != nil {
		t.Fatalf("failed to insert test user: %v", err)
	}

	_, err = client.ExecuteUnlockUser(ctx, userID, 1)
	if err != nil {
		t.Fatalf("first execute failed: %v", err)
	}

	_, err = client.ExecuteUnlockUser(ctx, userID, 1)
	if err == nil {
		t.Error("SR-CMD-002: second execute should fail (no rows affected)")
	}
}

func TestPostgresIntegration_ConnectionFailure(t *testing.T) {
	badDSN := "postgres://baduser:badpass@localhost:5432/baddb?sslmode=disable&connect_timeout=1"

	_, err := NewClient(badDSN)
	if err == nil {
		t.Error("should fail with bad credentials")
	}
}

func BenchmarkExecuteUnlockUser(b *testing.B) {
	dsn := getTestDSN()
	client, err := NewClient(dsn)
	if err != nil {
		b.Skipf("PostgreSQL not available: %v", err)
	}
	defer client.Close()

	createTableSQL := `
		DROP TABLE IF EXISTS users CASCADE;
		CREATE TABLE users (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			email TEXT NOT NULL UNIQUE,
			locked BOOLEAN NOT NULL DEFAULT FALSE,
			status TEXT NOT NULL DEFAULT 'active',
			failed_login_count INTEGER NOT NULL DEFAULT 0,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
		);
	`

	if _, err := client.db.Exec(createTableSQL); err != nil {
		b.Fatalf("failed to create table: %v", err)
	}

	ctx := context.Background()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		userID := fmt.Sprintf("00000000-0000-0000-0000-%012d", i)
		email := fmt.Sprintf("bench%d@example.com", i)

		insertSQL := `INSERT INTO users (id, email, locked, status) VALUES ($1, $2, $3, $4)`
		_, err := client.db.ExecContext(ctx, insertSQL, userID, email, true, "active")
		if err != nil {
			b.Fatalf("insert failed: %v", err)
		}

		_, err = client.ExecuteUnlockUser(ctx, userID, 1)
		if err != nil {
			b.Fatalf("execute failed: %v", err)
		}
	}
}
