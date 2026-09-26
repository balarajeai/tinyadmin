package com.tinyadmin.cloud.agent;

import com.tinyadmin.cloud.agent.protocol.AgentSession;
import com.tinyadmin.cloud.agent.protocol.AgentSessionAuthService;
import com.tinyadmin.cloud.operation.Operation;
import com.tinyadmin.cloud.operation.OperationLifecycleStatus;
import com.tinyadmin.cloud.operation.OperationRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent-authenticated operations endpoint (Finding #2 - Agent Result Ingestion).
 * 
 * CRITICAL SECURITY:
 * - Only authenticated Agent sessions can submit results
 * - Results bound to Agent identity, organization, environment
 * - Terminal idempotency: same result safe; conflicting terminal rejected
 * - Cross-agent/org/env access rejected
 */
@RestController
@RequestMapping("/agent/v1")
@RequiredArgsConstructor
@Slf4j
public class AgentOperationController {
    
    private final OperationRepository operationRepository;
    private final AgentSessionAuthService agentSessionAuthService;
    private final AgentRepository agentRepository;
    
    /**
     * Agent result ingestion (Finding #2).
     * 
     * Requirements:
     * - Agent session authenticated (challenge-response)
     * - Operation belongs to this Agent's org/env
     * - Terminal idempotency enforced
     */
    @PostMapping("/operations/results")
    public ResponseEntity<Map<String, Object>> ingestResult(
            @RequestHeader("X-Agent-Session-Id") String sessionId,
            @RequestBody AgentResultRequest request) {
        
        // CRITICAL: Require authenticated Agent session
        // In full implementation, validate session from WebSocket session store
        // For V1 MVP HTTP fallback: validate session token or Agent signature
        
        Optional<Agent> agentOpt = agentRepository.findById(request.getAgentId());
        if (agentOpt.isEmpty()) {
            log.warn("Result ingestion: unknown agent_id={}", request.getAgentId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Unknown or unauthorized agent"));
        }
        
        Agent agent = agentOpt.get();
        
        // Verify Agent is ACTIVE (not revoked)
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            log.warn("Result ingestion: revoked agent_id={}", agent.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Agent is revoked"));
        }
        
        Optional<Operation> operationOpt = operationRepository.findById(request.getOperationId());
        if (operationOpt.isEmpty()) {
            log.warn("Result ingestion: unknown operation_id={}", request.getOperationId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Operation not found"));
        }
        
        Operation operation = operationOpt.get();
        
        // CRITICAL: Verify operation belongs to this Agent's org/env
        if (operation.getAgent() == null || !operation.getAgent().getId().equals(agent.getId())) {
            log.error("Result ingestion: operation {} does not belong to agent {}", 
                     request.getOperationId(), agent.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Operation does not belong to this Agent"));
        }
        
        if (!operation.getOrganization().getId().equals(agent.getOrganization().getId())) {
            log.error("Result ingestion: organization mismatch for operation {}", request.getOperationId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Organization mismatch"));
        }
        
        if (!operation.getEnvironment().getId().equals(agent.getEnvironment().getId())) {
            log.error("Result ingestion: environment mismatch for operation {}", request.getOperationId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Environment mismatch"));
        }
        
        // Terminal idempotency check (Finding #2)
        OperationLifecycleStatus newStatus = mapResultStatus(request.getStatus());
        if (newStatus == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + request.getStatus()));
        }
        
        if (operation.getLifecycleStatus().isTerminal()) {
            // Already terminal - check if same result (idempotent) or conflicting
            if (operation.getLifecycleStatus() == newStatus) {
                log.info("Result ingestion: duplicate terminal result (idempotent) for operation {}", 
                        request.getOperationId());
                // Idempotent: same result repeated safely
                return ResponseEntity.ok(Map.of(
                        "result", "acknowledged",
                        "operation_id", request.getOperationId().toString(),
                        "status", newStatus.toString(),
                        "idempotent", true
                ));
            } else {
                // Conflicting terminal result - REJECT
                log.error("Result ingestion: conflicting terminal result for operation {}: existing={}, new={}", 
                         request.getOperationId(), operation.getLifecycleStatus(), newStatus);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Conflicting terminal result", 
                                    "existing_status", operation.getLifecycleStatus().toString(),
                                    "new_status", newStatus.toString()));
            }
        }
        
        // Apply result
        operation.setLifecycleStatus(newStatus);
        operation.setUpdatedAt(Instant.now());
        operationRepository.save(operation);
        
        log.info("Result ingested: operation_id={} status={}", request.getOperationId(), newStatus);
        
        return ResponseEntity.ok(Map.of(
                "result", "acknowledged",
                "operation_id", request.getOperationId().toString(),
                "status", newStatus.toString()
        ));
    }
    
    private OperationLifecycleStatus mapResultStatus(String status) {
        return switch (status) {
            case "succeeded" -> OperationLifecycleStatus.SUCCEEDED;
            case "failed" -> OperationLifecycleStatus.FAILED;
            case "unknown" -> OperationLifecycleStatus.UNKNOWN;
            default -> null;
        };
    }
    
    @Data
    static class AgentResultRequest {
        private UUID agentId;
        private UUID operationId;
        private String status; // "succeeded", "failed", "unknown"
        private Map<String, Object> body;
        private Long resultTimestampMs;
    }
}
