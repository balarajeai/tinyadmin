package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Cloud → Agent: command(authorization envelope + mutation payload)
 * Mutating command delivery per Issue #3 §6.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandMessage extends AgentMessage {
    /**
     * Command type: "discover", "search", "preview", "execute", "rollback", etc.
     */
    @JsonProperty("command_type")
    private String commandType;
    
    /**
     * Cloud-signed authorization envelope (§6.1).
     * Contains kid, operation_id, org/env/agent/connection bindings,
     * mutation_payload_sha256, max_affected_records, iat, exp, signature.
     */
    @JsonProperty("authorization")
    private Map<String, Object> authorization;
    
    /**
     * Structured mutation payload (never arbitrary SQL/Mongo text).
     * Canonical form: action_or_field_op, targets, parameters, max_affected_records.
     */
    @JsonProperty("payload")
    private Map<String, Object> payload;
}
