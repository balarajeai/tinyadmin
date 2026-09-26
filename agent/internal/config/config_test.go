package config

import (
	"testing"
)

func TestConfig_ValidateWSS_Success(t *testing.T) {
	cfg := &Config{
		Agent: AgentConfig{
			ID:             "test-agent",
			OrganizationID: "test-org",
			EnvironmentID:  "test-env",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "wss://cloud.example.com/agent",
			CommandSigningKey: "test-key",
		},
		Connections: []ConnectionEntry{
			{
				ID:             "conn-1",
				OrganizationID: "test-org",
				EnvironmentID:  "test-env",
				SecretRef:      "secret-ref",
			},
		},
		Storage: StorageConfig{
			Path: "/tmp/test-storage.db",
		},
	}

	err := cfg.Validate()
	if err != nil {
		t.Errorf("expected no error for wss://, got: %v", err)
	}
}

func TestConfig_ValidateWSS_RejectWS(t *testing.T) {
	cfg := &Config{
		Agent: AgentConfig{
			ID:             "test-agent",
			OrganizationID: "test-org",
			EnvironmentID:  "test-env",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "ws://cloud.example.com/agent",
			CommandSigningKey: "test-key",
		},
		Connections: []ConnectionEntry{
			{
				ID:             "conn-1",
				OrganizationID: "test-org",
				EnvironmentID:  "test-env",
				SecretRef:      "secret-ref",
			},
		},
		Storage: StorageConfig{
			Path: "/tmp/test-storage.db",
		},
	}

	err := cfg.Validate()
	if err == nil {
		t.Error("expected error for ws://")
	}
}

func TestConfig_ValidateWSS_RejectHTTP(t *testing.T) {
	cfg := &Config{
		Agent: AgentConfig{
			ID:             "test-agent",
			OrganizationID: "test-org",
			EnvironmentID:  "test-env",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "http://cloud.example.com/agent",
			CommandSigningKey: "test-key",
		},
		Connections: []ConnectionEntry{
			{
				ID:             "conn-1",
				OrganizationID: "test-org",
				EnvironmentID:  "test-env",
				SecretRef:      "secret-ref",
			},
		},
		Storage: StorageConfig{
			Path: "/tmp/test-storage.db",
		},
	}

	err := cfg.Validate()
	if err == nil {
		t.Error("expected error for http://")
	}
}

func TestConfig_ValidateWSS_RejectHTTPS(t *testing.T) {
	cfg := &Config{
		Agent: AgentConfig{
			ID:             "test-agent",
			OrganizationID: "test-org",
			EnvironmentID:  "test-env",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "https://cloud.example.com/agent",
			CommandSigningKey: "test-key",
		},
		Connections: []ConnectionEntry{
			{
				ID:             "conn-1",
				OrganizationID: "test-org",
				EnvironmentID:  "test-env",
				SecretRef:      "secret-ref",
			},
		},
		Storage: StorageConfig{
			Path: "/tmp/test-storage.db",
		},
	}

	err := cfg.Validate()
	if err == nil {
		t.Error("expected error for https://")
	}
}

func TestConfig_ValidateWSS_RejectNoScheme(t *testing.T) {
	cfg := &Config{
		Agent: AgentConfig{
			ID:             "test-agent",
			OrganizationID: "test-org",
			EnvironmentID:  "test-env",
			PrivateKeyPath: "/path/to/key",
		},
		Cloud: CloudConfig{
			Endpoint:          "cloud.example.com/agent",
			CommandSigningKey: "test-key",
		},
		Connections: []ConnectionEntry{
			{
				ID:             "conn-1",
				OrganizationID: "test-org",
				EnvironmentID:  "test-env",
				SecretRef:      "secret-ref",
			},
		},
		Storage: StorageConfig{
			Path: "/tmp/test-storage.db",
		},
	}

	err := cfg.Validate()
	if err == nil {
		t.Error("expected error for missing scheme")
	}
}
