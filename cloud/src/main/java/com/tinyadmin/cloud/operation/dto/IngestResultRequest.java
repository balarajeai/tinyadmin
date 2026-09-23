package com.tinyadmin.cloud.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class IngestResultRequest {
    @NotNull
    private UUID operationId;
    
    @NotBlank
    private String resultStatus;
    
    private String beforeState;
    
    private String afterState;
}
