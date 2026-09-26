package com.tinyadmin.cloud.operation.dto;

import com.tinyadmin.cloud.operation.OperationKind;
import com.tinyadmin.cloud.operation.OperationLifecycleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OperationResponse {
    private UUID id;
    private UUID organizationId;
    private UUID environmentId;
    private UUID agentId;
    private UUID connectionId;
    private UUID actorUserId;
    private OperationKind kind;
    private UUID actionDefinitionId;
    private String target;
    private String parameters;
    private OperationLifecycleStatus lifecycleStatus;
    private UUID previewId;
    private UUID confirmationId;
    private UUID parentOperationId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant terminalAt;
}
