package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent → Cloud: challenge_response(signature by Agent private key)
 * Agent proves possession of private key by signing the challenge nonce.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChallengeResponseMessage extends AgentMessage {
    /**
     * Ed25519 signature over the challenge nonce (base64url-encoded).
     * Signed with Agent's enrolled private key.
     */
    @JsonProperty("signature")
    private String signature;
}
