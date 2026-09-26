package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Cloud → Agent: challenge(nonce)
 * Cloud issues fresh nonce for Agent to sign with private key.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeMessage extends AgentMessage {
    /**
     * Fresh cryptographic nonce (base64url-encoded).
     * Agent must sign this with Ed25519 private key.
     */
    @JsonProperty("nonce")
    private String nonce;
}
