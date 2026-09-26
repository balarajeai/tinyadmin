package com.tinyadmin.cloud.operation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConfirmOperationRequest {
    @NotNull
    private UUID organizationId;
    
    @NotNull
    private UUID operationId;
    
    @NotNull
    private UUID actorUserId;
    
    private String previewFingerprint;
    
    private Boolean productionAck;
}
