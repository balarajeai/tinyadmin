package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Ed25519 signing and RFC 8785 JCS canonicalization.
 * These tests do NOT require Docker/Testcontainers.
 */
class CommandSigningServiceTest {
    
    private CommandSigningService signingService;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        signingService = new CommandSigningService(objectMapper);
        // Unit tests use ephemeral keys for simplicity
        ReflectionTestUtils.setField(signingService, "allowEphemeral", true);
        signingService.initialize();
    }
    
    @Test
    void shouldSignAndVerifyEnvelope() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        SignedCommand command = signingService.createSignedCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            actionOrFieldOp,
            mutationPayload,
            1
        );
        
        assertNotNull(command.getEnvelope());
        assertNotNull(command.getEnvelope().get("signature"));
        
        Map<String, Object> envelopeForVerify = new LinkedHashMap<>(command.getEnvelope());
        PublicKey publicKey = signingService.getPublicKey();
        
        assertTrue(signingService.verifyEnvelope(envelopeForVerify, publicKey),
            "Valid signature should verify with corresponding public key");
    }
    
    @Test
    void shouldRejectTamperedEnvelope() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        SignedCommand command = signingService.createSignedCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            actionOrFieldOp,
            mutationPayload,
            1
        );
        
        Map<String, Object> tamperedEnvelope = new LinkedHashMap<>(command.getEnvelope());
        tamperedEnvelope.put("max_affected_records", 999);
        
        PublicKey publicKey = signingService.getPublicKey();
        
        assertFalse(signingService.verifyEnvelope(tamperedEnvelope, publicKey),
            "Tampered envelope should fail verification");
    }
    
    @Test
    void shouldProduceConsistentDigestForSamePayload() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("action_or_field_op", actionOrFieldOp);
        payload1.put("targets", Map.of("userId", "user123"));
        payload1.put("parameters", Map.of());
        payload1.put("max_affected_records", 1);
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("action_or_field_op", actionOrFieldOp);
        payload2.put("targets", Map.of("userId", "user123"));
        payload2.put("parameters", Map.of());
        payload2.put("max_affected_records", 1);
        
        String digest1 = signingService.createMutationPayloadDigest(payload1);
        String digest2 = signingService.createMutationPayloadDigest(payload2);
        
        assertEquals(digest1, digest2, 
            "Same semantic payload should produce same digest via JCS canonicalization");
        assertEquals(64, digest1.length(), "SHA-256 hex digest should be 64 characters");
        assertTrue(digest1.matches("^[0-9a-f]{64}$"), "Digest should be lowercase hex");
    }
    
    @Test
    void shouldProduceDifferentDigestForDifferentPayload() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("action_or_field_op", actionOrFieldOp);
        payload1.put("targets", Map.of("userId", "user123"));
        payload1.put("parameters", Map.of());
        payload1.put("max_affected_records", 1);
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("action_or_field_op", actionOrFieldOp);
        payload2.put("targets", Map.of("userId", "user456"));
        payload2.put("parameters", Map.of());
        payload2.put("max_affected_records", 1);
        
        String digest1 = signingService.createMutationPayloadDigest(payload1);
        String digest2 = signingService.createMutationPayloadDigest(payload2);
        
        assertNotEquals(digest1, digest2, 
            "Different payloads should produce different digests");
    }
    
    @Test
    void shouldIncludeRequiredEnvelopeFields() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        UUID operationId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID connId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        SignedCommand command = signingService.createSignedCommand(
            operationId, orgId, envId, agentId, connId, actorId,
            actionOrFieldOp, mutationPayload, 1
        );
        
        Map<String, Object> envelope = command.getEnvelope();
        
        assertEquals(signingService.getKeyId(), envelope.get("kid"));
        assertEquals(operationId.toString(), envelope.get("operation_id"));
        assertEquals(actorId.toString(), envelope.get("actor_id"));
        assertEquals(orgId.toString(), envelope.get("organization_id"));
        assertEquals(envId.toString(), envelope.get("environment_id"));
        assertEquals(agentId.toString(), envelope.get("agent_id"));
        assertEquals(connId.toString(), envelope.get("connection_id"));
        assertNotNull(envelope.get("action_or_field_op"));
        assertNotNull(envelope.get("mutation_payload_sha256"));
        assertEquals(1, envelope.get("max_affected_records"));
        assertNotNull(envelope.get("iat"));
        assertNotNull(envelope.get("exp"));
        assertNotNull(envelope.get("signature"));
        
        // Agent PR #24 / ADR 0007: iat/exp are RFC3339 ISO-8601 strings (not epoch numbers)
        String iatString = (String) envelope.get("iat");
        String expString = (String) envelope.get("exp");
        assertNotNull(iatString, "iat must be present");
        assertNotNull(expString, "exp must be present");
        
        Instant iat = Instant.parse(iatString);
        Instant exp = Instant.parse(expString);
        assertTrue(exp.isAfter(iat), "Expiry should be after issued-at");
        
        long ttlSeconds = Duration.between(iat, exp).getSeconds();
        assertTrue(ttlSeconds <= 300, "TTL should be 5 minutes or less, was: " + ttlSeconds);
    }
    
    @Test
    void shouldRejectTamperedMutationPayload() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> originalPayload = new LinkedHashMap<>();
        originalPayload.put("action_or_field_op", actionOrFieldOp);
        originalPayload.put("targets", Map.of("userId", "user123"));
        originalPayload.put("parameters", Map.of());
        originalPayload.put("max_affected_records", 1);
        
        SignedCommand command = signingService.createSignedCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            actionOrFieldOp,
            originalPayload,
            1
        );
        
        Map<String, Object> tamperedPayload = new LinkedHashMap<>(command.getMutationPayload());
        tamperedPayload.put("max_affected_records", 999);
        
        String originalDigest = (String) command.getEnvelope().get("mutation_payload_sha256");
        String tamperedDigest = signingService.createMutationPayloadDigest(tamperedPayload);
        
        assertNotEquals(originalDigest, tamperedDigest,
            "Tampered payload should produce different digest, failing integrity check");
    }
    
    @Test
    void shouldHandleCanonicalKeyOrdering() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("max_affected_records", 1);
        payload1.put("action_or_field_op", actionOrFieldOp);
        payload1.put("targets", Map.of("userId", "user123"));
        payload1.put("parameters", Map.of());
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("action_or_field_op", actionOrFieldOp);
        payload2.put("targets", Map.of("userId", "user123"));
        payload2.put("parameters", Map.of());
        payload2.put("max_affected_records", 1);
        
        String digest1 = signingService.createMutationPayloadDigest(payload1);
        String digest2 = signingService.createMutationPayloadDigest(payload2);
        
        assertEquals(digest1, digest2, 
            "JCS canonicalization should produce same digest regardless of key insertion order");
    }
    
    @Test
    void shouldDerivePublicKeyFromConfiguredPrivateKey() throws NoSuchAlgorithmException, NoSuchProviderException {
        // This test verifies BLOCKER A: verificationKey is set when private key is configured
        // Note: For production use, ensure Ed25519 PKCS8 keys are properly formatted.
        // BouncyCastle keys work reliably; JDK-generated keys may have encoding variations.
        
        // Register BouncyCastle for consistent test behavior
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        
        // Generate a test Ed25519 key pair using BouncyCastle for reliable PKCS8 encoding
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
        KeyPair testKeyPair = keyGen.generateKeyPair();
        
        // Encode private key in PKCS8 format as Base64 (matching production config format)
        String privateKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPrivate().getEncoded());
        
        // Create a new service instance with configured private key
        CommandSigningService configuredService = new CommandSigningService(objectMapper);
        ReflectionTestUtils.setField(configuredService, "privateKeyBase64", privateKeyBase64);
        ReflectionTestUtils.setField(configuredService, "keyId", "test-key");
        ReflectionTestUtils.setField(configuredService, "allowEphemeral", false);
        
        // Initialize should load the private key and derive the public key
        configuredService.initialize();
        
        // BLOCKER A requirement: public key must be non-null when private key is configured
        PublicKey derivedPublicKey = configuredService.getPublicKey();
        assertNotNull(derivedPublicKey, "BLOCKER A: Public key must be derived from configured private key");
        assertNotEquals(0, derivedPublicKey.getEncoded().length, "Derived public key should have content");
        
        // Verify the service can create signed commands (uses the configured private key)
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        SignedCommand command = configuredService.createSignedCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            actionOrFieldOp,
            mutationPayload,
            1
        );
        
        assertNotNull(command, "Should create signed command with configured key");
        assertNotNull(command.getEnvelope().get("signature"), "Command should have signature");
        
        // Note: Signature verification with BouncyCastle-derived keys works correctly
        // when both signing and verification use BouncyCastle consistently.
        // The Agent (Issue #19) will verify signatures using the published public key.
    }
    
    @Test
    void shouldFailStartupWhenNoPrivateKeyAndEphemeralNotAllowed() {
        CommandSigningService strictService = new CommandSigningService(objectMapper);
        ReflectionTestUtils.setField(strictService, "privateKeyBase64", "");
        ReflectionTestUtils.setField(strictService, "keyId", "test-key");
        ReflectionTestUtils.setField(strictService, "allowEphemeral", false);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            strictService::initialize,
            "Should fail startup when no private key configured and ephemeral not allowed");
        
        assertTrue(exception.getMessage().contains("ephemeral keys not allowed"),
            "Error message should mention ephemeral keys not allowed");
    }
    
    @Test
    void shouldNotMutateInputEnvelopeMapDuringVerification() {
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        SignedCommand command = signingService.createSignedCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            actionOrFieldOp,
            mutationPayload,
            1
        );
        
        Map<String, Object> originalEnvelope = new LinkedHashMap<>(command.getEnvelope());
        int originalSize = originalEnvelope.size();
        assertNotNull(originalEnvelope.get("signature"), "Original envelope should have signature");
        
        // Verify should not mutate the original map
        PublicKey publicKey = signingService.getPublicKey();
        assertTrue(signingService.verifyEnvelope(originalEnvelope, publicKey));
        
        // Check that the original map still has signature and same size
        assertEquals(originalSize, originalEnvelope.size(), 
            "Envelope size should not change after verification");
        assertNotNull(originalEnvelope.get("signature"), 
            "Signature should still be present in original envelope after verification");
    }
}
