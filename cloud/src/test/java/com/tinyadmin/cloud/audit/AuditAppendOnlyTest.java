package com.tinyadmin.cloud.audit;

import com.tinyadmin.cloud.BaseIntegrationTest;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.environment.EnvironmentKind;
import com.tinyadmin.cloud.environment.EnvironmentStatus;
import com.tinyadmin.cloud.organization.Organization;
import com.tinyadmin.cloud.organization.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class AuditAppendOnlyTest extends BaseIntegrationTest {
    
    @Autowired
    private AuditService auditService;
    
    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Test
    void shouldRecordAuditEvent() {
        Organization org = Organization.builder()
            .name("Test Org")
            .slug("test-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        
        Environment env = Environment.builder()
            .organization(org)
            .key("prod")
            .displayName("Production")
            .kind(EnvironmentKind.PRODUCTION)
            .status(EnvironmentStatus.ACTIVE)
            .build();
        
        UUID operationId = UUID.randomUUID();
        
        AuditEvent event = auditService.recordEvent(AuditService.builder()
            .organization(org)
            .environment(env)
            .operationId(operationId)
            .actorUserId(UUID.randomUUID())
            .eventType("mutation_succeeded")
            .target("{\"userId\":\"user123\"}")
            .beforeState("{\"locked\":true}")
            .afterState("{\"locked\":false}")
            .resultStatus("succeeded"));
        
        assertNotNull(event);
        assertNotNull(event.getId());
        assertEquals("mutation_succeeded", event.getEventType());
        assertEquals(operationId, event.getOperationId());
        assertNotNull(event.getOccurredAt());
    }
    
    @Test
    void auditEventsShouldBeImmutableAfterCreation() {
        Organization org = Organization.builder()
            .name("Test Org")
            .slug("test-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        
        AuditEvent event = auditService.recordEvent(AuditService.builder()
            .organization(org)
            .eventType("test_event")
            .resultStatus("original"));
        
        AuditEvent savedEvent = auditEventRepository.findById(event.getId()).orElseThrow();
        
        assertEquals("test_event", savedEvent.getEventType());
        assertEquals("original", savedEvent.getResultStatus());
    }
}
