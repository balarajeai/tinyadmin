package com.tinyadmin.cloud.operation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ExecuteOperationRequest {
    @NotNull
    private UUID organizationId;
    
    @NotNull
    private UUID operationId;
    
    @NotNull
    private UUID actorUserId;
}
