package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Cloud → Agent: result_ack message_type
 * Per Agent PR #24 e3a006885cb79f0123da8404feb4c7cf4b81272f.
 * Simple acknowledgment; Agent clears durable result on acknowledged=true.
 * Agent does NOT verify signature.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultAckMessage extends AgentMessage {
    @JsonProperty("operation_id")
    private String operationId;
    
    @JsonProperty("acknowledged")
    private Boolean acknowledged;
}
