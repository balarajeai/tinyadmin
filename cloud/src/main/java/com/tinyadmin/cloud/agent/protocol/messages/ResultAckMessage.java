package com.tinyadmin.cloud.agent.protocol.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Cloud → Agent: result_ack(operation_id, ack_signature)
 * Authenticated acknowledgment (SEC-PR12-005 / CR-PR12-004).
 * Agent MUST verify authenticity before deleting durable result.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultAckMessage extends AgentMessage {
    private UUID operationId;
    
    /**
     * Cloud-signed acknowledgment over canonical ack payload (base64url).
     * Proves Cloud received and persisted result.
     * Agent verifies with Cloud command-signing public key.
     */
    private String ackSignature;
    
    /**
     * Canonical ack payload digest for verification (hex SHA-256).
     * Payload: {"operation_id":"<uuid>","status":"acked","iat":<timestamp>}
     */
    private String ackPayloadDigest;
}
