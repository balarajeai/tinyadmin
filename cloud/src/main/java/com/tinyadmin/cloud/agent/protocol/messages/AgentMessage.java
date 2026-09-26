package com.tinyadmin.cloud.agent.protocol.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Base class for Agent↔Cloud protocol messages (Issue #3).
 * Uses Jackson polymorphic deserialization by message_type.
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "message_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionHelloMessage.class, name = "session_hello"),
    @JsonSubTypes.Type(value = ChallengeMessage.class, name = "challenge"),
    @JsonSubTypes.Type(value = ChallengeResponseMessage.class, name = "challenge_response"),
    @JsonSubTypes.Type(value = SessionOkMessage.class, name = "session_ok"),
    @JsonSubTypes.Type(value = ResultMessage.class, name = "result"),
    @JsonSubTypes.Type(value = ResultAckMessage.class, name = "result_ack"),
    @JsonSubTypes.Type(value = CancelRevokeSyncRequestMessage.class, name = "cancel_revoke_sync_request"),
    @JsonSubTypes.Type(value = CancelRevokeSyncResponseMessage.class, name = "cancel_revoke_sync_response"),
    @JsonSubTypes.Type(value = CommandMessage.class, name = "command")
})
public abstract class AgentMessage {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("protocol_version")
    private Integer protocolVersion;
}
