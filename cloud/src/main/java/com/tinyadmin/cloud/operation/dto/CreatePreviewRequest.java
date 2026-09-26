package com.tinyadmin.cloud.operation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreatePreviewRequest {
    @NotNull
    private UUID organizationId;
    
    @NotNull
    private UUID environmentId;
    
    @NotNull
    private UUID agentId;
    
    @NotNull
    private UUID connectionId;
    
    @NotNull
    private UUID actionDefinitionId;
    
    @NotNull
    private UUID actorUserId;
    
    @NotNull
    private Map<String, Object> target;
}
