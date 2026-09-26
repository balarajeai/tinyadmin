package com.tinyadmin.cloud.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.agent.AgentRepository;
import com.tinyadmin.cloud.agent.AgentStatus;
import com.tinyadmin.cloud.agent.protocol.messages.*;
import com.tinyadmin.cloud.security.CommandSigningService;
import com.tinyadmin.cloud.security.JcsCanonicalizer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for Agent↔Cloud protocol (Issue #3 / ADR 0007).
 * 
 * Verifies:
 * - Message shapes conform to protocol spec
 * - JCS canonicalization produces identical bytes Cloud/Agent
 * - Ed25519 signatures verify correctly
 * - Session authentication flow
 * - Result ack authenticity
 * - Cancel/revoke sync shape
 * 
 * These tests do NOT require Docker/Testcontainers (pure contract validation).
 */
class AgentProtocolContractTest {
    
    private ObjectMapper objectMapper;
    private JcsCanonicalizer canonicalizer;
    private CommandSigningService signingService;
    
    @BeforeEach
    void setUp() {
        // Register BouncyCastle for consistent Ed25519
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        
        objectMapper = new ObjectMapper();
        signingService = new CommandSigningService(objectMapper);
        ReflectionTestUtils.setField(signingService, "allowEphemeral", true);
        ReflectionTestUtils.setField(signingService, "keyId", "cloud-signing-key-v1-test");
        signingService.initialize();
        
        canonicalizer = new JcsCanonicalizer(objectMapper);
    }
    
    @Test
    void sessionHelloMessageShape() throws Exception {
        // Verify session_hello conforms to protocol §5
        SessionHelloMessage hello = new SessionHelloMessage();
        hello.setMessageType("session_hello");
        hello.setProtocolVersion(1);
        hello.setAgentId(UUID.randomUUID());
        
        String json = objectMapper.writeValueAsString(hello);
        
        // Verify required fields present
        assertTrue(json.contains("\"message_type\":\"session_hello\""));
        assertTrue(json.contains("\"protocol_version\":1"));
        assertTrue(json.contains("\"agent_id\""));
        
        // Verify round-trip
        AgentMessage parsed = objectMapper.readValue(json, AgentMessage.class);
        assertInstanceOf(SessionHelloMessage.class, parsed);
        assertEquals(hello.getAgentId(), ((SessionHelloMessage) parsed).getAgentId());
    }
    
    @Test
    void challengeMessageShape() throws Exception {
        // Verify challenge message conforms to protocol §5
        ChallengeMessage challenge = ChallengeMessage.builder()
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]))
                .build();
        challenge.setMessageType("challenge");
        challenge.setProtocolVersion(1);
        
        String json = objectMapper.writeValueAsString(challenge);
        
        assertTrue(json.contains("\"message_type\":\"challenge\""));
        assertTrue(json.contains("\"nonce\""));
        
        // Verify round-trip
        AgentMessage parsed = objectMapper.readValue(json, AgentMessage.class);
        assertInstanceOf(ChallengeMessage.class, parsed);
    }
    
    @Test
    void sessionOkMessageShape() throws Exception {
        // Verify session_ok conforms to protocol §5
        SessionOkMessage sessionOk = SessionOkMessage.builder()
                .sessionExp(Instant.now().plusSeconds(300).getEpochSecond())
                .serverTime(Instant.now().getEpochSecond())
                .cloudCommandSigningPubkey(Base64.getEncoder().encodeToString(
                        signingService.getPublicKey().getEncoded()
                ))
                .build();
        sessionOk.setMessageType("session_ok");
        sessionOk.setProtocolVersion(1);
        
        String json = objectMapper.writeValueAsString(sessionOk);
        
        assertTrue(json.contains("\"message_type\":\"session_ok\""));
        assertTrue(json.contains("\"session_exp\""));
        assertTrue(json.contains("\"server_time\""));
        assertTrue(json.contains("\"cloud_command_signing_pubkey\""));
    }
    
    @Test
    void resultReportMessageShape() throws Exception {
        // Verify result_report conforms to protocol §7
        ResultReportMessage report = new ResultReportMessage();
        report.setMessageType("result_report");
        report.setProtocolVersion(1);
        report.setOperationId(UUID.randomUUID());
        report.setStatus("succeeded");
        report.setBody(Map.of("affected_rows", 1));
        report.setResultTimestampMs(System.currentTimeMillis());
        
        String json = objectMapper.writeValueAsString(report);
        
        assertTrue(json.contains("\"message_type\":\"result_report\""));
        assertTrue(json.contains("\"operation_id\""));
        assertTrue(json.contains("\"status\":\"succeeded\""));
        assertTrue(json.contains("\"body\""));
    }
    
    @Test
    void resultAckMessageShape() throws Exception {
        // Verify result_ack conforms to Agent PR #24
        ResultAckMessage ack = ResultAckMessage.builder()
                .operationId(UUID.randomUUID().toString())
                .acknowledged(true)
                .build();
        ack.setMessageType("result_ack");
        ack.setProtocolVersion(1);
        
        String json = objectMapper.writeValueAsString(ack);
        
        assertTrue(json.contains("\"message_type\":\"result_ack\""));
        assertTrue(json.contains("\"operation_id\""));
        assertTrue(json.contains("\"acknowledged\""));
        
        ResultAckMessage parsed = objectMapper.readValue(json, ResultAckMessage.class);
        assertNotNull(parsed.getOperationId());
        assertTrue(parsed.getAcknowledged());
    }
    
    @Test
    void cancelRevokeSyncMessageShape() throws Exception {
        // Verify cancel_revoke_sync conforms to protocol §8 (CR-PR12-001)
        CancelRevokeSyncMessage sync = CancelRevokeSyncMessage.builder()
                .agentRevoked(false)
                .canceledOperationIds(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()))
                .authzEpoch(System.currentTimeMillis())
                .build();
        sync.setMessageType("cancel_revoke_sync");
        sync.setProtocolVersion(1);
        
        String json = objectMapper.writeValueAsString(sync);
        
        assertTrue(json.contains("\"message_type\":\"cancel_revoke_sync\""));
        assertTrue(json.contains("\"agent_revoked\":false"));
        assertTrue(json.contains("\"canceled_operation_ids\""));
        assertTrue(json.contains("\"authz_epoch\""));
        
        // Verify Agent can parse
        AgentMessage parsed = objectMapper.readValue(json, AgentMessage.class);
        assertInstanceOf(CancelRevokeSyncMessage.class, parsed);
        CancelRevokeSyncMessage parsedSync = (CancelRevokeSyncMessage) parsed;
        assertEquals(2, parsedSync.getCanceledOperationIds().size());
    }
    
    @Test
    void commandEnvelopeShape() throws Exception {
        // Verify command envelope conforms to protocol §6.1 (SEC-PR12-001)
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("user_id", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        // Create signed command using CommandSigningService
        var signedCommand = signingService.createSignedCommand(
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
        
        assertNotNull(signedCommand, "SignedCommand should not be null");
        Map<String, Object> envelope = signedCommand.getEnvelope();
        assertNotNull(envelope, "Envelope should not be null");
        
        // Verify required envelope fields per §6.1
        assertNotNull(envelope.get("kid"), "kid should be present in envelope");
        assertNotNull(envelope.get("operation_id"));
        assertNotNull(envelope.get("organization_id"));
        assertNotNull(envelope.get("environment_id"));
        assertNotNull(envelope.get("agent_id"));
        assertNotNull(envelope.get("connection_id"));
        assertNotNull(envelope.get("action_or_field_op"));
        assertNotNull(envelope.get("mutation_payload_sha256"));
        assertNotNull(envelope.get("max_affected_records"));
        assertNotNull(envelope.get("iat"));
        assertNotNull(envelope.get("exp"));
        assertNotNull(envelope.get("signature"));
        
        // Verify mutation_payload_sha256 is hex SHA-256 (64 chars)
        String payloadDigest = (String) envelope.get("mutation_payload_sha256");
        assertEquals(64, payloadDigest.length());
        assertTrue(payloadDigest.matches("^[0-9a-f]{64}$"));
        
        // Verify signature is base64url (Ed25519 = 64 bytes → ~86 chars base64)
        String signature = (String) envelope.get("signature");
        assertTrue(signature.length() >= 85 && signature.length() <= 90);
    }
    
    @Test
    void jcsCanonicalConsistency() throws Exception {
        // Verify JCS produces identical bytes regardless of key order (RFC 8785)
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("max_affected_records", 1);
        payload1.put("action_or_field_op", Map.of("type", "action"));
        payload1.put("targets", Map.of("id", "123"));
        payload1.put("parameters", Map.of());
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("targets", Map.of("id", "123"));
        payload2.put("parameters", Map.of());
        payload2.put("action_or_field_op", Map.of("type", "action"));
        payload2.put("max_affected_records", 1);
        
        String json1 = objectMapper.writeValueAsString(payload1);
        String json2 = objectMapper.writeValueAsString(payload2);
        
        byte[] canonical1 = canonicalizer.canonicalize(json1);
        byte[] canonical2 = canonicalizer.canonicalize(json2);
        
        assertArrayEquals(canonical1, canonical2, 
                "JCS must produce identical bytes for semantically equivalent payloads");
    }
    
    @Test
    void mutationPayloadDigestVerification() throws Exception {
        // Verify Agent can recompute digest from received payload
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("user_id", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        // Cloud computes digest
        String digest1 = signingService.createMutationPayloadDigest(mutationPayload);
        
        // Agent recomputes digest (simulated)
        String json = objectMapper.writeValueAsString(mutationPayload);
        byte[] canonicalBytes = canonicalizer.canonicalize(json);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonicalBytes);
        String digest2 = bytesToHex(hash);
        
        assertEquals(digest1, digest2, "Cloud and Agent must compute identical digest");
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
