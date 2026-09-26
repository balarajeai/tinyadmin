package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * Agent → Cloud: result_report(operation_id, status, body)
 * Durable result delivery from Agent after execution or rejection.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResultReportMessage extends AgentMessage {
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
    @JsonProperty("body")
    private Map<String, Object> body;
    
    /**
     * Agent's timestamp when result was persisted locally (milliseconds).
     */
    @JsonProperty("result_timestamp_ms")
    private Long resultTimestampMs;
}
