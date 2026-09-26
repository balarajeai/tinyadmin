package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Agent → Cloud: cancel_revoke_sync_request(agent_id)
 * Agent requests current cancel/revoke state on reconnect.
 * Per Agent PR #24 @ e3a006885cb79f0123da8404feb4c7cf4b81272f
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRevokeSyncRequestMessage extends AgentMessage {
    
    @JsonProperty("agent_id")
    private UUID agentId;
}
