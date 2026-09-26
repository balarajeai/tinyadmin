package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Agent → Cloud: result(operation_id, status, result, error_message, protocol_version)
 * Durable result delivery from Agent after execution or rejection.
 * Per Agent PR #24 @ e3a006885cb79f0123da8404feb4c7cf4b81272f
 * 
 * CRITICAL: message_type is "result" (not "result_report")
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultMessage extends AgentMessage {
    
    @JsonProperty("operation_id")
    private UUID operationId;
    
    /**
     * Terminal status: "succeeded", "failed", or "unknown".
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * Result body (engine-specific; may include affected rows, error details, etc.).
     */
    @JsonProperty("result")
    private Map<String, Object> result;
    
    /**
     * Error message when status is "failed".
     */
    @JsonProperty("error_message")
    private String errorMessage;
}
