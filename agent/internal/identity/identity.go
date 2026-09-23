package identity

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
)

type Identity struct {
	privateKey ed25519.PrivateKey
	publicKey  ed25519.PublicKey
}

func Generate() (*Identity, error) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("failed to generate Ed25519 keypair: %w", err)
	}

	return &Identity{
		privateKey: priv,
		publicKey:  pub,
	}, nil
}

func LoadPrivateKey(path string) (*Identity, error) {
	if path == "" {
		return nil, errors.New("private key path cannot be empty")
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read private key: %w", err)
	}

	decoded, err := hex.DecodeString(string(data))
	if err != nil {
		return nil, fmt.Errorf("failed to decode private key: %w", err)
	}

	if len(decoded) != ed25519.PrivateKeySize {
		return nil, fmt.Errorf("invalid private key size: expected %d, got %d", ed25519.PrivateKeySize, len(decoded))
	}

	priv := ed25519.PrivateKey(decoded)
	pub := priv.Public().(ed25519.PublicKey)

	return &Identity{
		privateKey: priv,
		publicKey:  pub,
	}, nil
}

func (i *Identity) SavePrivateKey(path string) error {
	if path == "" {
		return errors.New("private key path cannot be empty")
	}

	encoded := hex.EncodeToString(i.privateKey)
	
	if err := os.WriteFile(path, []byte(encoded), 0600); err != nil {
		return fmt.Errorf("failed to write private key: %w", err)
	}

	return nil
}

func (i *Identity) Sign(message []byte) []byte {
	return ed25519.Sign(i.privateKey, message)
}

func (i *Identity) PublicKey() ed25519.PublicKey {
	return i.publicKey
}

func (i *Identity) PublicKeyHex() string {
	return hex.EncodeToString(i.publicKey)
}

func VerifySignature(publicKey ed25519.PublicKey, message, signature []byte) bool {
	return ed25519.Verify(publicKey, message, signature)
}
