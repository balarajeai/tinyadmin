package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cloud → Agent: cancel_revoke_sync_response(cancelled_operations)
 * Authoritative cancel/revoke state sent in response to Agent request.
 * Per Agent PR #24 @ e3a006885cb79f0123da8404feb4c7cf4b81272f
 * 
 * CRITICAL: UK spelling "cancelled_operations" (not US "canceled")
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRevokeSyncResponseMessage extends AgentMessage {
    
    /**
     * List of operation IDs that have been cancelled and MUST NOT be executed.
     * UK spelling per Agent PR #24 wire contract.
     */
    @JsonProperty("cancelled_operations")
    private List<String> cancelledOperations;
}
