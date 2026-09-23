package config

import (
	"errors"
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Agent       AgentConfig       `yaml:"agent"`
	Cloud       CloudConfig       `yaml:"cloud"`
	Connections []ConnectionEntry `yaml:"connections"`
	Storage     StorageConfig     `yaml:"storage"`
}

type AgentConfig struct {
	ID             string `yaml:"id"`
	OrganizationID string `yaml:"organization_id"`
	EnvironmentID  string `yaml:"environment_id"`
	PrivateKeyPath string `yaml:"private_key_path"`
}

type CloudConfig struct {
	Endpoint            string        `yaml:"endpoint"`
	CommandSigningKey   string        `yaml:"command_signing_key"`
	ReconnectBaseDelay  time.Duration `yaml:"reconnect_base_delay"`
	ReconnectMaxDelay   time.Duration `yaml:"reconnect_max_delay"`
	HeartbeatInterval   time.Duration `yaml:"heartbeat_interval"`
	ClockSkewTolerance  time.Duration `yaml:"clock_skew_tolerance"`
}

type ConnectionEntry struct {
	ID             string `yaml:"id"`
	OrganizationID string `yaml:"organization_id"`
	EnvironmentID  string `yaml:"environment_id"`
	SecretRef      string `yaml:"secret_ref"`
}

type StorageConfig struct {
	Path string `yaml:"path"`
}

func LoadFromFile(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	if err := cfg.Validate(); err != nil {
		return nil, fmt.Errorf("invalid config: %w", err)
	}

	cfg.setDefaults()

	return &cfg, nil
}

func (c *Config) Validate() error {
	if c.Agent.ID == "" {
		return errors.New("agent.id is required")
	}
	if c.Agent.OrganizationID == "" {
		return errors.New("agent.organization_id is required")
	}
	if c.Agent.EnvironmentID == "" {
		return errors.New("agent.environment_id is required")
	}
	if c.Agent.PrivateKeyPath == "" {
		return errors.New("agent.private_key_path is required")
	}

	if c.Cloud.Endpoint == "" {
		return errors.New("cloud.endpoint is required")
	}
	if c.Cloud.CommandSigningKey == "" {
		return errors.New("cloud.command_signing_key is required")
	}

	if len(c.Connections) == 0 {
		return errors.New("at least one connection is required")
	}

	for i, conn := range c.Connections {
		if conn.ID == "" {
			return fmt.Errorf("connection[%d].id is required", i)
		}
		if conn.OrganizationID == "" {
			return fmt.Errorf("connection[%d].organization_id is required", i)
		}
		if conn.EnvironmentID == "" {
			return fmt.Errorf("connection[%d].environment_id is required", i)
		}
		if conn.SecretRef == "" {
			return fmt.Errorf("connection[%d].secret_ref is required", i)
		}

		if conn.OrganizationID != c.Agent.OrganizationID {
			return fmt.Errorf("connection[%d].organization_id must match agent.organization_id", i)
		}
		if conn.EnvironmentID != c.Agent.EnvironmentID {
			return fmt.Errorf("connection[%d].environment_id must match agent.environment_id", i)
		}
	}

	if c.Storage.Path == "" {
		return errors.New("storage.path is required")
	}

	return nil
}

func (c *Config) setDefaults() {
	if c.Cloud.ReconnectBaseDelay == 0 {
		c.Cloud.ReconnectBaseDelay = 1 * time.Second
	}
	if c.Cloud.ReconnectMaxDelay == 0 {
		c.Cloud.ReconnectMaxDelay = 60 * time.Second
	}
	if c.Cloud.HeartbeatInterval == 0 {
		c.Cloud.HeartbeatInterval = 30 * time.Second
	}
	if c.Cloud.ClockSkewTolerance == 0 {
		c.Cloud.ClockSkewTolerance = 5 * time.Minute
	}
}

func (c *Config) GetConnection(connectionID string) (*ConnectionEntry, error) {
	for _, conn := range c.Connections {
		if conn.ID == connectionID {
			return &conn, nil
		}
	}
	return nil, fmt.Errorf("connection %s not found in allowlist", connectionID)
}
