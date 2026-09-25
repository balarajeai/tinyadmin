package com.tinyadmin.cloud.agent.protocol;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an authenticated Agent WebSocket session.
 * Bound to agent_id, organization_id, environment_id per ADR 0006 P-AGENT-ID.
 */
@Data
@Builder
public class AgentSession {
    
    private String sessionId;
    private UUID agentId;
    private UUID organizationId;
    private UUID environmentId;
    private Instant sessionExp;
    private Instant establishedAt;
    private boolean authenticated;
    
    /**
     * Pending challenge for authentication (null after authenticated).
     */
    private byte[] pendingChallengeNonce;
    
    /**
     * Protocol version negotiated.
     */
    private int protocolVersion;
}
