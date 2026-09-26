package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Cloud → Agent: cancel_revoke_sync(agent_revoked, canceled_operation_ids[], authz_epoch)
 * Authoritative cancel/revoke state sent on reconnect before pending mutates (CR-PR12-001).
 * Agent MUST apply this state and discard canceled/expired ops before executing any pending work.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRevokeSyncMessage extends AgentMessage {
    /**
     * True if Agent is revoked; Agent MUST fail closed (no mutation execution).
     */
    @JsonProperty("agent_revoked")
    private Boolean agentRevoked;
    
    /**
     * List of operation_ids that have been canceled and MUST NOT be executed.
     */
    @JsonProperty("canceled_operation_ids")
    private List<UUID> canceledOperationIds;
    
    /**
     * Optional authorization invalidation version/epoch marker.
     * Agent can track this to detect stale local authorization cache.
     */
    @JsonProperty("authz_epoch")
    private Long authzEpoch;
}
