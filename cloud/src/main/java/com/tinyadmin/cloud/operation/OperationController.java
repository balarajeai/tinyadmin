package com.tinyadmin.cloud.operation;

import com.tinyadmin.cloud.operation.dto.*;
import com.tinyadmin.cloud.security.SignedCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Slf4j
public class OperationController {
    
    private final OperationService operationService;
    
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> createPreview(
        @Valid @RequestBody CreatePreviewRequest request
    ) {
        log.info("Creating preview for action: {}", request.getActionDefinitionId());
        
        // CRITICAL: actor_id from authenticated session, NOT client-supplied
        Operation operation = operationService.createPreview(
            request.getOrganizationId(),
            request.getEnvironmentId(),
            request.getAgentId(),
            request.getConnectionId(),
            request.getActionDefinitionId(),
            request.getTarget()
        );
        
        return ResponseEntity.ok(Map.of(
            "operationId", operation.getId(),
            "previewId", operation.getPreviewId(),
            "status", operation.getLifecycleStatus()
        ));
    }
    
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmOperation(
        @Valid @RequestBody ConfirmOperationRequest request
    ) {
        log.info("Confirming operation: {}", request.getOperationId());
        
        // CRITICAL: actor_id from authenticated session, NOT client-supplied
        Confirmation confirmation = operationService.confirmOperation(
            request.getOrganizationId(),
            request.getOperationId(),
            request.getPreviewFingerprint(),
            request.getProductionAck()
        );
        
        return ResponseEntity.ok(Map.of(
            "confirmationId", confirmation.getId(),
            "operationId", confirmation.getOperationId(),
            "confirmedAt", confirmation.getConfirmedAt()
        ));
    }
    
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeOperation(
        @Valid @RequestBody ExecuteOperationRequest request
    ) {
        log.info("Executing operation: {}", request.getOperationId());
        
        // CRITICAL: actor_id from authenticated session, NOT client-supplied
        SignedCommand command = operationService.executeOperation(
            request.getOrganizationId(),
            request.getOperationId()
        );
        
        return ResponseEntity.ok(Map.of(
            "operationId", request.getOperationId(),
            "command", command
        ));
    }
    
    @PostMapping("/results")
    public ResponseEntity<Map<String, Object>> ingestResult(
        @Valid @RequestBody IngestResultRequest request
    ) {
        log.info("Ingesting result for operation: {}", request.getOperationId());
        
        operationService.ingestResult(
            request.getOperationId(),
            request.getResultStatus(),
            request.getBeforeState(),
            request.getAfterState()
        );
        
        return ResponseEntity.ok(Map.of(
            "operationId", request.getOperationId(),
            "status", "ingested"
        ));
    }
    
    @GetMapping("/{operationId}")
    public ResponseEntity<OperationResponse> getOperation(
        @PathVariable UUID operationId,
        @RequestParam UUID organizationId
    ) {
        Operation operation = operationService.getOperation(organizationId, operationId);
        
        OperationResponse response = OperationResponse.builder()
            .id(operation.getId())
            .organizationId(operation.getOrganization().getId())
            .environmentId(operation.getEnvironment().getId())
            .agentId(operation.getAgent() != null ? operation.getAgent().getId() : null)
            .connectionId(operation.getConnection() != null ? operation.getConnection().getId() : null)
            .actorUserId(operation.getActorUserId())
            .kind(operation.getKind())
            .actionDefinitionId(operation.getActionDefinition() != null ? operation.getActionDefinition().getId() : null)
            .target(operation.getTarget())
            .parameters(operation.getParameters())
            .lifecycleStatus(operation.getLifecycleStatus())
            .previewId(operation.getPreviewId())
            .confirmationId(operation.getConfirmationId())
            .parentOperationId(operation.getParentOperationId())
            .createdAt(operation.getCreatedAt())
            .updatedAt(operation.getUpdatedAt())
            .terminalAt(operation.getTerminalAt())
            .build();
        
        return ResponseEntity.ok(response);
    }
}
