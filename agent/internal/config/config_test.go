package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestConfigValidation(t *testing.T) {
	tests := []struct {
		name    string
		config  Config
		wantErr bool
		errMsg  string
	}{
		{
			name: "valid config",
			config: Config{
				Agent: AgentConfig{
					ID:             "agent-1",
					OrganizationID: "org-1",
					EnvironmentID:  "env-1",
					PrivateKeyPath: "/path/to/key",
				},
				Cloud: CloudConfig{
					Endpoint:          "wss://cloud.example.com",
					CommandSigningKey: "abcd1234",
				},
				Connections: []ConnectionEntry{
					{
						ID:             "conn-1",
						OrganizationID: "org-1",
						EnvironmentID:  "env-1",
						SecretRef:      "DB_DSN",
					},
				},
				Storage: StorageConfig{
					Path: "/tmp/agent.db",
				},
			},
			wantErr: false,
		},
		{
			name: "missing agent id",
			config: Config{
				Agent: AgentConfig{
					OrganizationID: "org-1",
					EnvironmentID:  "env-1",
				},
				Cloud: CloudConfig{
					Endpoint:          "wss://cloud.example.com",
					CommandSigningKey: "abcd1234",
				},
				Connections: []ConnectionEntry{
					{ID: "conn-1", OrganizationID: "org-1", EnvironmentID: "env-1", SecretRef: "DB_DSN"},
				},
				Storage: StorageConfig{Path: "/tmp/agent.db"},
			},
			wantErr: true,
			errMsg:  "agent.id is required",
		},
		{
			name: "connection org mismatch",
			config: Config{
				Agent: AgentConfig{
					ID:             "agent-1",
					OrganizationID: "org-1",
					EnvironmentID:  "env-1",
					PrivateKeyPath: "/path/to/key",
				},
				Cloud: CloudConfig{
					Endpoint:          "wss://cloud.example.com",
					CommandSigningKey: "abcd1234",
				},
				Connections: []ConnectionEntry{
					{
						ID:             "conn-1",
						OrganizationID: "org-2",
						EnvironmentID:  "env-1",
						SecretRef:      "DB_DSN",
					},
				},
				Storage: StorageConfig{Path: "/tmp/agent.db"},
			},
			wantErr: true,
			errMsg:  "connection[0].organization_id must match agent.organization_id",
		},
		{
			name: "connection env mismatch",
			config: Config{
				Agent: AgentConfig{
					ID:             "agent-1",
					OrganizationID: "org-1",
					EnvironmentID:  "env-1",
					PrivateKeyPath: "/path/to/key",
				},
				Cloud: CloudConfig{
					Endpoint:          "wss://cloud.example.com",
					CommandSigningKey: "abcd1234",
				},
				Connections: []ConnectionEntry{
					{
						ID:             "conn-1",
						OrganizationID: "org-1",
						EnvironmentID:  "env-2",
						SecretRef:      "DB_DSN",
					},
				},
				Storage: StorageConfig{Path: "/tmp/agent.db"},
			},
			wantErr: true,
			errMsg:  "connection[0].environment_id must match agent.environment_id",
		},
		{
			name: "no connections",
			config: Config{
				Agent: AgentConfig{
					ID:             "agent-1",
					OrganizationID: "org-1",
					EnvironmentID:  "env-1",
					PrivateKeyPath: "/path/to/key",
				},
				Cloud: CloudConfig{
					Endpoint:          "wss://cloud.example.com",
					CommandSigningKey: "abcd1234",
				},
				Connections: []ConnectionEntry{},
				Storage:     StorageConfig{Path: "/tmp/agent.db"},
			},
			wantErr: true,
			errMsg:  "at least one connection is required",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.config.Validate()
			if tt.wantErr {
				if err == nil {
					t.Fatalf("expected error containing %q, got nil", tt.errMsg)
				}
				if tt.errMsg != "" && err.Error() != tt.errMsg {
					t.Errorf("expected error %q, got %q", tt.errMsg, err.Error())
				}
			} else {
				if err != nil {
					t.Fatalf("unexpected error: %v", err)
				}
			}
		})
	}
}

func TestConfigDefaults(t *testing.T) {
	cfg := Config{
		Agent: AgentConfig{
			ID:             "agent-1",
			OrganizationID: "org-1",
			EnvironmentID:  "env-1",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "wss://cloud.example.com",
			CommandSigningKey: "abcd1234",
		},
		Connections: []ConnectionEntry{
			{ID: "conn-1", OrganizationID: "org-1", EnvironmentID: "env-1", SecretRef: "DB_DSN"},
		},
		Storage: StorageConfig{Path: "/tmp/agent.db"},
	}

	cfg.setDefaults()

	if cfg.Cloud.ReconnectBaseDelay != 1*time.Second {
		t.Errorf("expected ReconnectBaseDelay=1s, got %v", cfg.Cloud.ReconnectBaseDelay)
	}
	if cfg.Cloud.ReconnectMaxDelay != 60*time.Second {
		t.Errorf("expected ReconnectMaxDelay=60s, got %v", cfg.Cloud.ReconnectMaxDelay)
	}
	if cfg.Cloud.HeartbeatInterval != 30*time.Second {
		t.Errorf("expected HeartbeatInterval=30s, got %v", cfg.Cloud.HeartbeatInterval)
	}
	if cfg.Cloud.ClockSkewTolerance != 5*time.Minute {
		t.Errorf("expected ClockSkewTolerance=5m, got %v", cfg.Cloud.ClockSkewTolerance)
	}
}

func TestGetConnection(t *testing.T) {
	cfg := Config{
		Connections: []ConnectionEntry{
			{ID: "conn-1", SecretRef: "SECRET1"},
			{ID: "conn-2", SecretRef: "SECRET2"},
		},
	}

	conn, err := cfg.GetConnection("conn-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if conn.ID != "conn-1" || conn.SecretRef != "SECRET1" {
		t.Errorf("unexpected connection: %+v", conn)
	}

	_, err = cfg.GetConnection("conn-3")
	if err == nil {
		t.Fatal("expected error for non-existent connection")
	}
}

func TestLoadFromFile(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "config.yaml")

	validYAML := `
agent:
  id: agent-1
  organization_id: org-1
  environment_id: env-1
  private_key_path: /path/to/key

cloud:
  endpoint: wss://cloud.example.com
  command_signing_key: abcd1234

connections:
  - id: conn-1
    organization_id: org-1
    environment_id: env-1
    secret_ref: DB_DSN

storage:
  path: /tmp/agent.db
`

	if err := os.WriteFile(configPath, []byte(validYAML), 0600); err != nil {
		t.Fatalf("failed to write config file: %v", err)
	}

	cfg, err := LoadFromFile(configPath)
	if err != nil {
		t.Fatalf("failed to load config: %v", err)
	}

	if cfg.Agent.ID != "agent-1" {
		t.Errorf("expected agent_id=agent-1, got %s", cfg.Agent.ID)
	}
}
