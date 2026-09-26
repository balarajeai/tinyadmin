package connection

import (
	"errors"
	"fmt"
	"os"
)

type Manager struct {
	connections map[string]string
}

func NewManager(allowlist map[string]string) *Manager {
	return &Manager{
		connections: allowlist,
	}
}

func (m *Manager) Resolve(connectionID string) (string, error) {
	secretRef, exists := m.connections[connectionID]
	if !exists {
		return "", fmt.Errorf("connection %s not in allowlist", connectionID)
	}

	dsn := os.Getenv(secretRef)
	if dsn == "" {
		return "", fmt.Errorf("secret %s (connection %s) not found in environment", secretRef, connectionID)
	}

	return dsn, nil
}

func (m *Manager) ValidateConnection(connectionID, orgID, envID string, allowedOrg, allowedEnv string) error {
	if _, exists := m.connections[connectionID]; !exists {
		return fmt.Errorf("connection %s not in allowlist", connectionID)
	}

	if orgID != allowedOrg {
		return fmt.Errorf("connection organization mismatch: expected %s, got %s", allowedOrg, orgID)
	}

	if envID != allowedEnv {
		return fmt.Errorf("connection environment mismatch: expected %s, got %s", allowedEnv, envID)
	}

	return nil
}

func BuildSecretRefMap(connectionID string, secretRef string) (map[string]string, error) {
	if connectionID == "" {
		return nil, errors.New("connectionID cannot be empty")
	}
	if secretRef == "" {
		return nil, errors.New("secretRef cannot be empty")
	}

	return map[string]string{connectionID: secretRef}, nil
}
