package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Agent → Cloud: session_hello(agent_id, protocol_version)
 * First message after WebSocket upgrade; initiates challenge-response auth.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionHelloMessage extends AgentMessage {
    @JsonProperty("agent_id")
    private UUID agentId;
}
