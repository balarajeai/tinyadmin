package com.tinyadmin.cloud.agent;

import com.tinyadmin.cloud.agent.protocol.AgentSession;
import com.tinyadmin.cloud.agent.protocol.AgentSessionStore;
import com.tinyadmin.cloud.agent.protocol.ResultAckService;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent-authenticated operations endpoint (Finding SEC-PR23-002 - Agent Result Ingestion).
 * 
 * CRITICAL SECURITY PROPERTIES:
 * - Agent identity derived ONLY from validated authenticated session
 * - Client-supplied agent_id is FORBIDDEN and REJECTED
 * - Session validated from AgentSessionStore (post challenge-response auth)
 * - Results bound to session's Agent identity, organization, environment
 * - Terminal idempotency: same terminal result safe; conflicting terminal rejected
 * - Cross-agent/org/env access rejected
 * - Revoked/expired sessions rejected
 * 
 * Authentication mechanism:
 * - Requires X-Agent-Session-Id header from authenticated WSS session
 * - OR Agent-signed POST with Ed25519 signature (future HTTPS fallback)
 * - Session must exist in sessionStore (created after challenge-response)
 * 
 * V1: WSS session-based authentication
 * Future: Add HTTPS Agent-signed POST per Agent PR #24
 */
@RestController
@RequestMapping("/agent/v1")
@RequiredArgsConstructor
@Slf4j
public class AgentOperationController {
    
    private final OperationRepository operationRepository;
    private final AgentSessionStore sessionStore;
    private final AgentRepository agentRepository;
    private final ResultAckService resultAckService;
    
    /**
     * Agent result ingestion with authenticated session validation (SEC-PR23-002).
     * 
     * CRITICAL: Agent identity from validated session ONLY.
     * Client cannot supply agent_id.
     * 
     * Requirements:
     * - Agent session authenticated (challenge-response completed)
     * - Operation belongs to session's Agent's org/env
     * - Terminal idempotency enforced
     * 
     * @param sessionId X-Agent-Session-Id header (REQUIRED)
     * @param request Result report body (no agent_id - derived from session)
     * @return Acknowledgment or error
     */
    @PostMapping("/operations/results")
    public ResponseEntity<Map<String, Object>> ingestResult(
            @RequestHeader(value = "X-Agent-Session-Id", required = false) String sessionId,
            @RequestBody AgentResultRequest request) {
        
        // CRITICAL (SEC-PR23-002): Require authenticated session
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Result ingestion: missing X-Agent-Session-Id header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing X-Agent-Session-Id header"));
        }
        
        // CRITICAL: Validate session from store (authoritative source)
        Optional<AgentSession> sessionOpt = sessionStore.getValidatedSession(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Result ingestion: invalid or expired session_id={}", sessionId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired session"));
        }
        
        AgentSession session = sessionOpt.get();
        
        // CRITICAL: Derive Agent identity from session ONLY (not client input)
        UUID agentId = session.getAgentId();
        UUID sessionOrgId = session.getOrganizationId();
        UUID sessionEnvId = session.getEnvironmentId();
        
        // Verify Agent still exists and is ACTIVE
        Optional<Agent> agentOpt = agentRepository.findById(agentId);
        if (agentOpt.isEmpty()) {
            log.error("Result ingestion: Agent {} not found", agentId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Agent not found"));
        }
        
        Agent agent = agentOpt.get();
        
        // CRITICAL: Verify Agent is ACTIVE (not revoked)
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            log.warn("Result ingestion: Agent {} is {}, not ACTIVE", agentId, agent.getStatus());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Agent is revoked or inactive"));
        }
        
        // Find operation
        Optional<Operation> operationOpt = operationRepository.findById(request.getOperationId());
        if (operationOpt.isEmpty()) {
            log.warn("Result ingestion: unknown operation_id={}", request.getOperationId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Operation not found"));
        }
        
        Operation operation = operationOpt.get();
        
        // CRITICAL: Verify operation belongs to session's Agent
        if (operation.getAgent() == null || !operation.getAgent().getId().equals(agentId)) {
            log.error("Result ingestion: operation {} does not belong to agent {} (belongs to {})", 
                     request.getOperationId(), agentId, 
                     operation.getAgent() != null ? operation.getAgent().getId() : "none");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Operation does not belong to this Agent"));
        }
        
        // CRITICAL: Verify operation belongs to session's organization
        if (!operation.getOrganization().getId().equals(sessionOrgId)) {
            log.error("Result ingestion: operation {} org {} != session org {}", 
                     request.getOperationId(), operation.getOrganization().getId(), sessionOrgId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Organization mismatch"));
        }
        
        // CRITICAL: Verify operation belongs to session's environment
        if (!operation.getEnvironment().getId().equals(sessionEnvId)) {
            log.error("Result ingestion: operation {} env {} != session env {}", 
                     request.getOperationId(), operation.getEnvironment().getId(), sessionEnvId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Environment mismatch"));
        }
        
        // Map result status
        OperationLifecycleStatus newStatus = mapResultStatus(request.getStatus());
        if (newStatus == null) {
            log.warn("Result ingestion: invalid status={}", request.getStatus());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + request.getStatus()));
        }
        
        // CRITICAL: Terminal idempotency check (SEC-PR23-002)
        // - Same terminal result: accept (idempotent)
        // - Conflicting terminal result: reject
        if (operation.getLifecycleStatus().isTerminal()) {
            if (operation.getLifecycleStatus() == newStatus) {
                log.info("Result ingestion: duplicate terminal result (idempotent) for operation {}", 
                        request.getOperationId());
                // Idempotent: same result repeated safely
                // SEC-PR23-003: Return authenticated result_ack matching Agent PR #24
                return buildAuthenticatedAckResponse(request.getOperationId(), newStatus, true);
            } else {
                // Conflicting terminal result - REJECT
                log.error("Result ingestion: conflicting terminal result for operation {}: existing={}, new={}", 
                         request.getOperationId(), operation.getLifecycleStatus(), newStatus);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "Conflicting terminal result", 
                                "existing_status", operation.getLifecycleStatus().toString(),
                                "new_status", newStatus.toString()
                        ));
            }
        }
        
        // Apply result
        operation.setLifecycleStatus(newStatus);
        operation.setUpdatedAt(Instant.now());
        if (newStatus.isTerminal()) {
            operation.setTerminalAt(Instant.now());
        }
        operationRepository.save(operation);
        
        log.info("Result ingested: operation_id={} status={} agent_id={}", 
                request.getOperationId(), newStatus, agentId);
        
        // SEC-PR23-003: Return authenticated result_ack matching Agent PR #24 wire format
        return buildAuthenticatedAckResponse(request.getOperationId(), newStatus, false);
    }
    
    /**
     * Maps Agent result status string to Cloud lifecycle status.
     */
    private OperationLifecycleStatus mapResultStatus(String status) {
        return switch (status) {
            case "succeeded" -> OperationLifecycleStatus.SUCCEEDED;
            case "failed" -> OperationLifecycleStatus.FAILED;
            case "unknown" -> OperationLifecycleStatus.UNKNOWN;
            default -> null;
        };
    }
    
    /**
     * Builds authenticated result_ack response matching Agent PR #24 wire format (SEC-PR23-003).
     * 
     * Response shape per Issue #3 / Agent PR #24:
     * {
     *   "result": "acknowledged",
     *   "operation_id": "<uuid>",
     *   "status": "succeeded|failed|unknown",
     *   "idempotent": true|false (optional),
     *   "result_ack": {
     *     "ack_signature": "<base64url-encoded-ed25519-sig>",
     *     "ack_payload_digest": "<hex-sha256>"
     *   }
     * }
     * 
     * CRITICAL: Agent MUST verify ack_signature with Cloud command-signing public key
     * before deleting durable result.
     */
    private ResponseEntity<Map<String, Object>> buildAuthenticatedAckResponse(
            UUID operationId, 
            OperationLifecycleStatus status,
            boolean idempotent) {
        
        // Create authenticated ack with real Ed25519 signature
        Map<String, String> resultAck = resultAckService.createAuthenticatedAck(operationId);
        
        // Build response matching Agent PR #24 wire format
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", "acknowledged");
        response.put("operation_id", operationId.toString());
        response.put("status", status.toString().toLowerCase());
        if (idempotent) {
            response.put("idempotent", true);
        }
        response.put("result_ack", resultAck);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Agent result request DTO.
     * 
     * CRITICAL: No agent_id field - Agent identity from session ONLY.
     */
    @Data
    static class AgentResultRequest {
        private UUID operationId;
        private String status; // "succeeded", "failed", "unknown"
        private Map<String, Object> body;
        private Long resultTimestampMs;
    }
}
