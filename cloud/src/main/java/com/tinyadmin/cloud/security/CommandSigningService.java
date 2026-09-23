package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommandSigningService {
    
    private final ObjectMapper objectMapper;
    
    public String createMutationPayloadDigest(Map<String, Object> mutationPayload) {
        try {
            String canonicalJson = objectMapper.writeValueAsString(mutationPayload);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            
            return bytesToHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to create mutation payload digest", e);
            throw new RuntimeException("Failed to create mutation payload digest", e);
        }
    }
    
    public SignedCommand createSignedCommand(
        UUID operationId,
        UUID organizationId,
        UUID environmentId,
        UUID agentId,
        UUID connectionId,
        UUID actorUserId,
        String actionOrFieldOp,
        Map<String, Object> mutationPayload,
        int maxAffectedRecords
    ) {
        String payloadDigest = createMutationPayloadDigest(mutationPayload);
        
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kid", "cloud-signing-key-v1");
        envelope.put("operation_id", operationId.toString());
        envelope.put("organization_id", organizationId.toString());
        envelope.put("environment_id", environmentId.toString());
        envelope.put("agent_id", agentId.toString());
        envelope.put("connection_id", connectionId.toString());
        envelope.put("actor_id", actorUserId != null ? actorUserId.toString() : "system");
        envelope.put("action_or_field_op", actionOrFieldOp);
        envelope.put("mutation_payload_sha256", payloadDigest);
        envelope.put("max_affected_records", maxAffectedRecords);
        
        Instant now = Instant.now();
        envelope.put("iat", now.getEpochSecond());
        envelope.put("exp", now.plusSeconds(300).getEpochSecond());
        
        String signature = signEnvelope(envelope);
        envelope.put("signature", signature);
        
        return SignedCommand.builder()
            .envelope(envelope)
            .mutationPayload(mutationPayload)
            .build();
    }
    
    private String signEnvelope(Map<String, Object> envelope) {
        try {
            String canonicalEnvelope = objectMapper.writeValueAsString(envelope);
            byte[] signatureBytes = canonicalEnvelope.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (JsonProcessingException e) {
            log.error("Failed to sign envelope", e);
            throw new RuntimeException("Failed to sign envelope", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
