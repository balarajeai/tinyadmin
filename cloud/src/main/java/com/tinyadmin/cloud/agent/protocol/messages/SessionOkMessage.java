package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Cloud → Agent: session_ok(session_exp, server_time)
 * Confirms successful authentication; session is now authenticated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionOkMessage extends AgentMessage {
    /**
     * Unix timestamp (seconds) when session expires.
     * Agent should refresh before expiry.
     */
    @JsonProperty("session_exp")
    private Long sessionExp;
    
    /**
     * Current Cloud server time (Unix timestamp seconds) for clock skew detection.
     */
    @JsonProperty("server_time")
    private Long serverTime;
    
    /**
     * Cloud command-signing public key (base64-encoded X.509).
     * Agent uses this to verify command envelopes.
     */
    @JsonProperty("cloud_command_signing_pubkey")
    private String cloudCommandSigningPubkey;
}
