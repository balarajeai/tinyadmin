package main

import (
	"context"
	"encoding/hex"
	"flag"
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/balarajeai/tinyadmin/agent/internal/config"
	"github.com/balarajeai/tinyadmin/agent/internal/connection"
	"github.com/balarajeai/tinyadmin/agent/internal/identity"
	"github.com/balarajeai/tinyadmin/agent/internal/operation"
	"github.com/balarajeai/tinyadmin/agent/internal/storage"
)

func main() {
	configPath := flag.String("config", "config.yaml", "path to configuration file")
	flag.Parse()

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	if err := run(*configPath, logger); err != nil {
		log.Fatal(err)
	}
}

func run(configPath string, logger *slog.Logger) error {
	cfg, err := config.LoadFromFile(configPath)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	logger.Info("loaded configuration",
		"agent_id", cfg.Agent.ID,
		"org_id", cfg.Agent.OrganizationID,
		"env_id", cfg.Agent.EnvironmentID,
	)

	ident, err := identity.LoadPrivateKey(cfg.Agent.PrivateKeyPath)
	if err != nil {
		return fmt.Errorf("failed to load identity: %w", err)
	}

	logger.Info("loaded agent identity", "public_key", ident.PublicKeyHex())

	cloudPubKeyBytes, err := hex.DecodeString(cfg.Cloud.CommandSigningKey)
	if err != nil {
		return fmt.Errorf("failed to decode cloud public key: %w", err)
	}

	store, err := storage.NewStore(cfg.Storage.Path)
	if err != nil {
		return fmt.Errorf("failed to initialize storage: %w", err)
	}
	defer store.Close()

	logger.Info("initialized storage", "path", cfg.Storage.Path)

	connMap := make(map[string]string)
	for _, conn := range cfg.Connections {
		connMap[conn.ID] = conn.SecretRef
	}
	connMgr := connection.NewManager(connMap)

	opMgr := operation.NewManager(
		store,
		connMgr,
		cfg.Agent.ID,
		cfg.Agent.OrganizationID,
		cfg.Agent.EnvironmentID,
		cloudPubKeyBytes,
		cfg.Cloud.ClockSkewTolerance,
		logger,
	)

	logger.Info("agent initialized successfully")

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigChan
		logger.Info("received shutdown signal")
		cancel()
	}()

	logger.Info("agent running (press Ctrl+C to stop)")
	
	<-ctx.Done()
	
	logger.Info("agent shutting down")

	_ = opMgr

	return nil
}
