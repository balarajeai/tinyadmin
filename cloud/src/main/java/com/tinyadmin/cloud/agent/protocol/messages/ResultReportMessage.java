package com.tinyadmin.cloud.agent.protocol.messages;

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
    private UUID operationId;
    
    /**
     * Terminal status: "succeeded", "failed", or "unknown".
     */
    private String status;
    
    /**
     * Result body (engine-specific; may include affected rows, error details, etc.).
     */
    private Map<String, Object> body;
    
    /**
     * Agent's timestamp when result was persisted locally (milliseconds).
     */
    private Long resultTimestampMs;
}
