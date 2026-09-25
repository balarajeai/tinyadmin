package com.tinyadmin.cloud.agent.protocol.messages;

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
    @JsonSubTypes.Type(value = ResultReportMessage.class, name = "result_report"),
    @JsonSubTypes.Type(value = ResultAckMessage.class, name = "result_ack"),
    @JsonSubTypes.Type(value = CancelRevokeSyncMessage.class, name = "cancel_revoke_sync"),
    @JsonSubTypes.Type(value = CommandMessage.class, name = "command")
})
public abstract class AgentMessage {
    private String messageType;
    private Integer protocolVersion;
}
