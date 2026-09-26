package com.tinyadmin.cloud.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyadmin.cloud.action.ActionDefinition;
import com.tinyadmin.cloud.action.ActionDefinitionRepository;
import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.agent.AgentRepository;
import com.tinyadmin.cloud.audit.AuditService;
import com.tinyadmin.cloud.connection.Connection;
import com.tinyadmin.cloud.connection.ConnectionRepository;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.environment.EnvironmentRepository;
import com.tinyadmin.cloud.organization.Organization;
import com.tinyadmin.cloud.organization.OrganizationRepository;
import com.tinyadmin.cloud.security.CommandSigningService;
import com.tinyadmin.cloud.security.OperatorAuthenticationService;
import com.tinyadmin.cloud.security.SignedCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationService {
    
    private final OperationRepository operationRepository;
    private final PreviewRepository previewRepository;
    private final ConfirmationRepository confirmationRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final AgentRepository agentRepository;
    private final ConnectionRepository connectionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final CommandSigningService commandSigningService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final OperatorAuthenticationService operatorAuthService;
    
    @Transactional
    public Operation createPreview(
        UUID organizationId,
        UUID environmentId,
        UUID agentId,
        UUID connectionId,
        UUID actionDefinitionId,
        Map<String, Object> target
    ) {
        // CRITICAL: Get authoritative actor_id from authenticated session
        // Client-supplied actor_id is FORBIDDEN
        UUID actorUserId = operatorAuthService.getAuthenticatedActorId();
        
        // Validate operator has access to specified organization
        operatorAuthService.requireOrganizationAccess(organizationId);
        Organization org = organizationRepository.findById(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        Environment env = environmentRepository.findById(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found"));
        
        if (!env.getOrganization().getId().equals(organizationId)) {
            throw new SecurityException("Environment does not belong to organization");
        }
        
        Agent agent = agentRepository.findByIdAndOrganizationIdAndEnvironmentId(agentId, organizationId, environmentId)
            .orElseThrow(() -> new SecurityException("Agent not found or does not belong to org/env"));
        
        Connection conn = connectionRepository.findByIdAndOrganizationIdAndEnvironmentId(connectionId, organizationId, environmentId)
            .orElseThrow(() -> new SecurityException("Connection not found or does not belong to org/env"));
        
        if (!conn.getAgent().getId().equals(agentId)) {
            throw new SecurityException("Connection does not belong to specified Agent");
        }
        
        ActionDefinition action = actionDefinitionRepository.findById(actionDefinitionId)
            .orElseThrow(() -> new IllegalArgumentException("Action not found"));
        
        if (!action.getOrganization().getId().equals(organizationId)) {
            throw new SecurityException("Action does not belong to organization");
        }
        
        Operation operation = Operation.builder()
            .organization(org)
            .environment(env)
            .agent(agent)
            .connection(conn)
            .actorUserId(actorUserId)
            .kind(OperationKind.PREVIEW)
            .actionDefinition(action)
            .target(serializeToJson(target))
            .lifecycleStatus(OperationLifecycleStatus.PREVIEW_PENDING)
            .build();
        
        operation = operationRepository.save(operation);
        
        Preview preview = Preview.builder()
            .operationId(operation.getId())
            .organization(org)
            .environment(env)
            .agent(agent)
            .connection(conn)
            .actorUserId(actorUserId)
            .kind(OperationKind.ACTION_EXECUTE)
            .expectedAffectedCount(1)
            .expectedAffectedSummary("Preview: Unlock user")
            .status(PreviewStatus.SUCCEEDED)
            .build();
        
        preview = previewRepository.save(preview);
        
        operation.setPreviewId(preview.getId());
        // CRITICAL: Preview success is NOT mutation terminal success (Finding #7)
        operation.setLifecycleStatus(OperationLifecycleStatus.PREVIEWED);
        
        // Blocker #6: Server-authoritative preview fingerprint with FULL context
        // Compute from server-owned preview data (not client-supplied)
        String previewFingerprint = computePreviewFingerprint(operation, preview);
        operation.setPreviewFingerprint(previewFingerprint);
        
        operation = operationRepository.save(operation);
        
        auditService.recordEvent(AuditService.builder()
            .organization(org)
            .environment(env)
            .operationId(operation.getId())
            .actorUserId(actorUserId)
            .agentId(agentId)
            .connectionId(connectionId)
            .actionDefinitionId(actionDefinitionId)
            .eventType("preview_completed")
            .target(serializeToJson(target))
            .resultStatus("succeeded"));
        
        log.info("Preview created: operationId={}, actionId={}", operation.getId(), actionDefinitionId);
        
        return operation;
    }
    
    @Transactional
    public Confirmation confirmOperation(
        UUID organizationId,
        UUID operationId,
        String previewFingerprint,
        Boolean productionAck
    ) {
        // CRITICAL: Get authoritative actor_id from authenticated session
        UUID actorUserId = operatorAuthService.getAuthenticatedActorId();
        
        // Validate operator has access to specified organization
        operatorAuthService.requireOrganizationAccess(organizationId);
        Operation operation = operationRepository.findByIdAndOrganizationId(operationId, organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        
        if (operation.getPreviewId() == null) {
            throw new IllegalStateException("Cannot confirm operation without preview");
        }
        
        // Finding #8: Validate server-authoritative preview fingerprint
        // Client may echo fingerprint; Cloud compares to stored value
        if (previewFingerprint == null || previewFingerprint.isBlank()) {
            throw new SecurityException("Preview fingerprint required");
        }
        
        if (!previewFingerprint.equals(operation.getPreviewFingerprint())) {
            log.error("Preview fingerprint mismatch: operation={} expected={} got={}", 
                     operationId, operation.getPreviewFingerprint(), previewFingerprint);
            throw new SecurityException("Preview fingerprint mismatch - preview may have been modified");
        }
        
        // Finding #9: Action definition pinning
        // Confirm uses same Action as preview
        UUID previewActionId = operation.getActionDefinition() != null ? 
                operation.getActionDefinition().getId() : null;
        if (previewActionId == null) {
            throw new IllegalStateException("Operation has no action definition");
        }
        
        // Blocker #3: Production acknowledgment semantics
        // Production environment mutations require explicit production ack
        boolean isProduction = operation.getEnvironment() != null && 
                              "PRODUCTION".equals(operation.getEnvironment().getKind().toString());
        ActionDefinition action = operation.getActionDefinition();
        boolean requiresProductionAck = action != null && Boolean.TRUE.equals(action.getProductionExtraConfirm());
        
        if (isProduction && requiresProductionAck) {
            if (productionAck == null || !productionAck) {
                log.error("Production acknowledgment required but not provided: operation={} env={}", 
                         operationId, operation.getEnvironment().getKey());
                throw new SecurityException("Production acknowledgment required for this operation");
            }
            log.info("Production acknowledgment validated: operation={}", operationId);
        }
        
        Confirmation confirmation = Confirmation.builder()
            .operationId(operationId)
            .actorUserId(actorUserId)
            .previewFingerprint(previewFingerprint)
            .productionAck(productionAck)
            .build();
        
        confirmation = confirmationRepository.save(confirmation);
        
        // CRITICAL: Lifecycle state transition PREVIEWED → CONFIRMED
        if (operation.getLifecycleStatus() != OperationLifecycleStatus.PREVIEWED) {
            throw new IllegalStateException("Cannot confirm operation in state: " + operation.getLifecycleStatus());
        }
        
        operation.setConfirmationId(confirmation.getId());
        operation.setLifecycleStatus(OperationLifecycleStatus.CONFIRMED);
        operationRepository.save(operation);
        
        auditService.recordEvent(AuditService.builder()
            .organization(operation.getOrganization())
            .environment(operation.getEnvironment())
            .operationId(operationId)
            .actorUserId(actorUserId)
            .agentId(operation.getAgent() != null ? operation.getAgent().getId() : null)
            .connectionId(operation.getConnection() != null ? operation.getConnection().getId() : null)
            .actionDefinitionId(operation.getActionDefinition() != null ? operation.getActionDefinition().getId() : null)
            .eventType("confirmation_completed")
            .resultStatus("confirmed"));
        
        log.info("Operation confirmed: operationId={}", operationId);
        
        return confirmation;
    }
    
    @Transactional
    public SignedCommand executeOperation(
        UUID organizationId,
        UUID operationId
    ) {
        // CRITICAL: Get authoritative actor_id from authenticated session
        UUID actorUserId = operatorAuthService.getAuthenticatedActorId();
        
        // Validate operator has access to specified organization
        operatorAuthService.requireOrganizationAccess(organizationId);
        Operation operation = operationRepository.findByIdAndOrganizationId(operationId, organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        
        if (operation.getConfirmationId() == null) {
            throw new IllegalStateException("Cannot execute operation without confirmation");
        }
        
        if (operation.getActionDefinition() == null) {
            throw new IllegalStateException("Operation has no action definition");
        }
        
        // Finding #9: Action definition pinning
        // Execute must use same Action as preview/confirmation
        UUID actionId = operation.getActionDefinition().getId();
        
        // Verify Action still enabled
        ActionDefinition action = actionDefinitionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalStateException("Action not found"));
        
        if (action.getStatus() != com.tinyadmin.cloud.action.ActionStatus.ENABLED) {
            throw new SecurityException("Action is not enabled: " + action.getStatus());
        }
        
        auditService.recordEvent(AuditService.builder()
            .organization(operation.getOrganization())
            .environment(operation.getEnvironment())
            .operationId(operationId)
            .actorUserId(actorUserId)
            .agentId(operation.getAgent() != null ? operation.getAgent().getId() : null)
            .connectionId(operation.getConnection() != null ? operation.getConnection().getId() : null)
            .actionDefinitionId(operation.getActionDefinition().getId())
            .eventType("mutation_requested")
            .target(operation.getTarget())
            .resultStatus("pending"));
        
        Map<String, Object> actionOrFieldOp = new LinkedHashMap<>();
        actionOrFieldOp.put("type", "action");
        actionOrFieldOp.put("action_definition_id", operation.getActionDefinition().getId().toString());
        
        int maxAffected = operation.getActionDefinition().getMaxAffectedRecords() != null ? 
            operation.getActionDefinition().getMaxAffectedRecords() : 1;
        
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("action_or_field_op", actionOrFieldOp);
        mutationPayload.put("targets", parseJson(operation.getTarget()));
        mutationPayload.put("parameters", operation.getParameters() != null ? parseJson(operation.getParameters()) : Map.of());
        mutationPayload.put("max_affected_records", maxAffected);
        
        // CRITICAL: Lifecycle state transition CONFIRMED → PENDING_RESULT
        if (operation.getLifecycleStatus() != OperationLifecycleStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot execute operation in state: " + operation.getLifecycleStatus());
        }
        
        operation.setLifecycleStatus(OperationLifecycleStatus.PENDING_RESULT);
        operationRepository.save(operation);
        
        SignedCommand command = commandSigningService.createSignedCommand(
            operation.getId(),
            operation.getOrganization().getId(),
            operation.getEnvironment().getId(),
            operation.getAgent().getId(),
            operation.getConnection().getId(),
            actorUserId,
            actionOrFieldOp,
            mutationPayload,
            maxAffected
        );
        
        log.info("Signed command created: operationId={} (state: PENDING_RESULT)", operationId);
        
        return command;
    }
    
    @Transactional
    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
    
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
    
    public Operation getOperation(UUID organizationId, UUID operationId) {
        return operationRepository.findByIdAndOrganizationId(operationId, organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
    }
    
    /**
     * Compute server-authoritative preview fingerprint (Finding #8).
     * Based on server-owned preview data, not client-supplied values.
     * 
     * Uses SHA-256 hash of canonical preview representation.
     */
    /**
     * Computes server-authoritative preview fingerprint (Blocker #6).
     * 
     * CRITICAL: Fingerprint must pin ALL confirmation-critical context:
     * - operation/preview identity
     * - organization_id, environment_id, agent_id, connection_id
     * - action_definition_id
     * - target identity (user_id for Unlock User)
     * - expected affected records
     * - preview result/status
     * 
     * Prevents cross-operation, cross-action, cross-target, cross-environment replay.
     * Client cannot forge fingerprint; must echo server-issued value.
     */
    private String computePreviewFingerprint(Operation operation, Preview preview) {
        try {
            // Canonical preview representation with FULL context binding
            UUID orgId = preview.getOrganization() != null ? preview.getOrganization().getId() : null;
            UUID envId = preview.getEnvironment() != null ? preview.getEnvironment().getId() : null;
            UUID agentId = preview.getAgent() != null ? preview.getAgent().getId() : null;
            UUID connId = preview.getConnection() != null ? preview.getConnection().getId() : null;
            UUID actionId = operation.getActionDefinition() != null ? operation.getActionDefinition().getId() : null;
            
            String canonical = String.format(
                "operation_id:%s|preview_id:%s|org_id:%s|env_id:%s|agent_id:%s|conn_id:%s|action_id:%s|target:%s|kind:%s|expected_affected:%d|status:%s",
                preview.getOperationId(),
                preview.getId(),
                orgId != null ? orgId.toString() : "null",
                envId != null ? envId.toString() : "null",
                agentId != null ? agentId.toString() : "null",
                connId != null ? connId.toString() : "null",
                actionId != null ? actionId.toString() : "null",
                operation.getTarget() != null ? operation.getTarget() : "null",
                preview.getKind().toString(),
                preview.getExpectedAffectedCount() != null ? preview.getExpectedAffectedCount() : 0,
                preview.getStatus().toString()
            );
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes());
            return bytesToHex(hash);
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute preview fingerprint", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
