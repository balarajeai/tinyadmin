package com.tinyadmin.cloud.operation;

import com.tinyadmin.cloud.BaseIntegrationTest;
import com.tinyadmin.cloud.action.ActionDefinitionRepository;
import com.tinyadmin.cloud.agent.AgentRepository;
import com.tinyadmin.cloud.audit.AuditEvent;
import com.tinyadmin.cloud.audit.AuditEventRepository;
import com.tinyadmin.cloud.connection.ConnectionRepository;
import com.tinyadmin.cloud.environment.EnvironmentRepository;
import com.tinyadmin.cloud.organization.OrganizationRepository;
import com.tinyadmin.cloud.security.SignedCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class OperationFlowTest extends BaseIntegrationTest {
    
    @Autowired
    private OperationService operationService;
    
    @Autowired
    private OrganizationRepository organizationRepository;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private AgentRepository agentRepository;
    
    @Autowired
    private ConnectionRepository connectionRepository;
    
    @Autowired
    private ActionDefinitionRepository actionDefinitionRepository;
    
    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Autowired
    private ConfirmationRepository confirmationRepository;
    
    private static final UUID TEST_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_ENV_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_AGENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID TEST_CONN_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID TEST_ACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    
    @Test
    void shouldCompleteFullUnlockUserFlow() {
        UUID actorUserId = UUID.randomUUID();
        Map<String, Object> target = Map.of("userId", "user123", "email", "locked@example.com");
        
        Operation preview = operationService.createPreview(
            TEST_ORG_ID, TEST_ENV_ID, TEST_AGENT_ID, TEST_CONN_ID, TEST_ACTION_ID, actorUserId, target
        );
        
        assertNotNull(preview);
        assertEquals(OperationLifecycleStatus.SUCCEEDED, preview.getLifecycleStatus());
        assertNotNull(preview.getPreviewId());
        
        List<AuditEvent> previewAudit = auditEventRepository.findByOperationIdOrderByOccurredAtAsc(preview.getId());
        assertTrue(previewAudit.stream().anyMatch(e -> e.getEventType().equals("preview_completed")));
        
        String previewFingerprint = "preview-" + preview.getPreviewId();
        Confirmation confirmation = operationService.confirmOperation(
            TEST_ORG_ID, preview.getId(), actorUserId, previewFingerprint, true
        );
        
        assertNotNull(confirmation);
        assertEquals(preview.getId(), confirmation.getOperationId());
        assertEquals(Boolean.TRUE, confirmation.getProductionAck());
        
        List<AuditEvent> confirmAudit = auditEventRepository.findByOperationIdOrderByOccurredAtAsc(preview.getId());
        assertTrue(confirmAudit.stream().anyMatch(e -> e.getEventType().equals("confirmation_completed")));
        
        SignedCommand command = operationService.executeOperation(TEST_ORG_ID, preview.getId(), actorUserId);
        
        assertNotNull(command);
        assertNotNull(command.getEnvelope());
        assertNotNull(command.getMutationPayload());
        
        assertEquals(preview.getId().toString(), command.getEnvelope().get("operation_id"));
        assertEquals(TEST_ORG_ID.toString(), command.getEnvelope().get("organization_id"));
        assertEquals(TEST_ENV_ID.toString(), command.getEnvelope().get("environment_id"));
        assertTrue(command.getEnvelope().containsKey("mutation_payload_sha256"));
        
        List<AuditEvent> executeAudit = auditEventRepository.findByOperationIdOrderByOccurredAtAsc(preview.getId());
        assertTrue(executeAudit.stream().anyMatch(e -> e.getEventType().equals("mutation_requested")));
        
        String beforeState = "{\"locked\":true}";
        String afterState = "{\"locked\":false}";
        operationService.ingestResult(preview.getId(), "succeeded", beforeState, afterState);
        
        Operation completedOp = operationService.getOperation(TEST_ORG_ID, preview.getId());
        assertEquals(OperationLifecycleStatus.SUCCEEDED, completedOp.getLifecycleStatus());
        
        List<AuditEvent> finalAudit = auditEventRepository.findByOperationIdOrderByOccurredAtAsc(preview.getId());
        assertTrue(finalAudit.stream().anyMatch(e -> 
            e.getEventType().equals("mutation_succeeded") && 
            e.getBeforeState() != null && 
            e.getAfterState() != null
        ));
    }
    
    @Test
    void shouldRejectExecuteWithoutConfirmation() {
        UUID actorUserId = UUID.randomUUID();
        Map<String, Object> target = Map.of("userId", "user123");
        
        Operation preview = operationService.createPreview(
            TEST_ORG_ID, TEST_ENV_ID, TEST_AGENT_ID, TEST_CONN_ID, TEST_ACTION_ID, actorUserId, target
        );
        
        assertThrows(IllegalStateException.class, () -> {
            operationService.executeOperation(TEST_ORG_ID, preview.getId(), actorUserId);
        }, "Should not execute without confirmation");
    }
    
    @Test
    void shouldRecordUnknownResultStatus() {
        UUID actorUserId = UUID.randomUUID();
        Map<String, Object> target = Map.of("userId", "user123");
        
        Operation preview = operationService.createPreview(
            TEST_ORG_ID, TEST_ENV_ID, TEST_AGENT_ID, TEST_CONN_ID, TEST_ACTION_ID, actorUserId, target
        );
        
        operationService.confirmOperation(TEST_ORG_ID, preview.getId(), actorUserId, "fingerprint", true);
        operationService.executeOperation(TEST_ORG_ID, preview.getId(), actorUserId);
        
        operationService.ingestResult(preview.getId(), "unknown", null, null);
        
        Operation completedOp = operationService.getOperation(TEST_ORG_ID, preview.getId());
        assertEquals(OperationLifecycleStatus.UNKNOWN, completedOp.getLifecycleStatus());
        
        List<AuditEvent> audit = auditEventRepository.findByOperationIdOrderByOccurredAtAsc(preview.getId());
        assertTrue(audit.stream().anyMatch(e -> 
            e.getEventType().equals("mutation_unknown") && 
            "unknown".equals(e.getResultStatus())
        ));
    }
}
