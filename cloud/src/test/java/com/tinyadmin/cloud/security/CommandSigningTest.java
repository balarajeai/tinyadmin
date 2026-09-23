package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyadmin.cloud.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandSigningTest extends BaseIntegrationTest {
    
    @Autowired
    private CommandSigningService commandSigningService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void shouldCreateValidSignedCommand() {
        UUID operationId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", UUID.randomUUID().toString());
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", Map.of("userId", "user123"));
        mutationPayload.put("parameters", Map.of());
        mutationPayload.put("max_affected_records", 1);
        
        SignedCommand command = commandSigningService.createSignedCommand(
            operationId, organizationId, environmentId, agentId, connectionId, actorUserId,
            objectMapper.valueToTree(actionOrFieldOp).toString(), mutationPayload, 1
        );
        
        assertNotNull(command);
        assertNotNull(command.getEnvelope());
        assertNotNull(command.getMutationPayload());
        
        assertEquals(operationId.toString(), command.getEnvelope().get("operation_id"));
        assertEquals(organizationId.toString(), command.getEnvelope().get("organization_id"));
        assertEquals(environmentId.toString(), command.getEnvelope().get("environment_id"));
        assertEquals(agentId.toString(), command.getEnvelope().get("agent_id"));
        assertEquals(connectionId.toString(), command.getEnvelope().get("connection_id"));
        
        assertNotNull(command.getEnvelope().get("mutation_payload_sha256"));
        assertNotNull(command.getEnvelope().get("signature"));
        assertNotNull(command.getEnvelope().get("iat"));
        assertNotNull(command.getEnvelope().get("exp"));
        assertEquals("cloud-signing-key-v1", command.getEnvelope().get("kid"));
    }
    
    @Test
    void shouldCreateConsistentDigest() {
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("field1", "value1");
        payload1.put("field2", 123);
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("field1", "value1");
        payload2.put("field2", 123);
        
        String digest1 = commandSigningService.createMutationPayloadDigest(payload1);
        String digest2 = commandSigningService.createMutationPayloadDigest(payload2);
        
        assertEquals(digest1, digest2, "Same payload should produce same digest");
    }
    
    @Test
    void shouldCreateDifferentDigestForDifferentPayload() {
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("field1", "value1");
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("field1", "value2");
        
        String digest1 = commandSigningService.createMutationPayloadDigest(payload1);
        String digest2 = commandSigningService.createMutationPayloadDigest(payload2);
        
        assertNotEquals(digest1, digest2, "Different payloads should produce different digests");
    }
}
