package com.tinyadmin.cloud.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyadmin.cloud.security.CommandSigningService;
import com.tinyadmin.cloud.security.JcsCanonicalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Result acknowledgment service with authentication (SEC-PR12-005 / CR-PR12-004).
 * 
 * Cloud-signed result_ack proves Cloud received and persisted the result.
 * Agent MUST verify authenticity before deleting durable result.
 * 
 * HTTPS and WSS ack semantics are equivalent.
 */
@Service
@Slf4j
public class ResultAckService {
    
    private final CommandSigningService commandSigningService;
    private final JcsCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    
    public ResultAckService(
            CommandSigningService commandSigningService,
            ObjectMapper objectMapper) {
        this.commandSigningService = commandSigningService;
        this.objectMapper = objectMapper;
        this.canonicalizer = new JcsCanonicalizer(objectMapper);
    }
    
    /**
     * Creates authenticated result_ack for operation.
     * 
     * Canonical ack payload (RFC 8785 JCS):
     * {
     *   "operation_id": "<uuid>",
     *   "status": "acked",
     *   "iat": <unix-timestamp>
     * }
     * 
     * Cloud signs this payload; Agent verifies with Cloud command-signing public key.
     * 
     * @param operationId Operation being acknowledged
     * @return Map with ack_signature and ack_payload_digest for Agent verification
     */
    public Map<String, String> createAuthenticatedAck(UUID operationId) {
        try {
            // Build canonical ack payload
            Map<String, Object> ackPayload = new LinkedHashMap<>();
            ackPayload.put("operation_id", operationId.toString());
            ackPayload.put("status", "acked");
            ackPayload.put("iat", Instant.now().getEpochSecond());
            
            // Canonicalize and compute digest
            String json = objectMapper.writeValueAsString(ackPayload);
            byte[] canonicalBytes = canonicalizer.canonicalize(json);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalBytes);
            String ackPayloadDigest = bytesToHex(hash);
            
            // Sign canonical ack payload with Cloud command-signing key
            // Use same signing infrastructure as command envelopes
            byte[] signatureBytes = signAckPayload(canonicalBytes);
            String ackSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            
            Map<String, String> result = new LinkedHashMap<>();
            result.put("ack_signature", ackSignature);
            result.put("ack_payload_digest", ackPayloadDigest);
            
            log.debug("Created authenticated ack for operation_id={}", operationId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to create authenticated ack for operation_id={}", operationId, e);
            throw new RuntimeException("Failed to create authenticated result_ack", e);
        }
    }
    
    /**
     * Signs ack payload using Cloud command-signing key (Finding #3 - Real authenticated result_ack).
     * 
     * Uses Ed25519 signature over canonical ack payload.
     * Agent verifies with Cloud command-signing public key.
     * 
     * This provides explicit Cloud authenticity for both WSS and HTTPS paths.
     */
    private byte[] signAckPayload(byte[] canonicalBytes) {
        // Use CommandSigningService.signBytes() for explicit Ed25519 signature
        return commandSigningService.signBytes(canonicalBytes);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
