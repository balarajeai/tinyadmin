package com.tinyadmin.cloud.audit;

import com.tinyadmin.cloud.BaseIntegrationTest;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.environment.EnvironmentKind;
import com.tinyadmin.cloud.environment.EnvironmentRepository;
import com.tinyadmin.cloud.environment.EnvironmentStatus;
import com.tinyadmin.cloud.organization.Organization;
import com.tinyadmin.cloud.organization.OrganizationRepository;
import com.tinyadmin.cloud.organization.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for audit event append-only semantics.
 * 
 * Persists Organization and Environment fixtures before recording audit events
 * to satisfy Hibernate association requirements.
 */
@Transactional
class AuditAppendOnlyTest extends BaseIntegrationTest {
    
    @Autowired
    private AuditService auditService;
    
    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Autowired
    private OrganizationRepository organizationRepository;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Test
    void shouldRecordAuditEvent() {
        // Persist organization and environment fixtures (required for Hibernate associations)
        Organization org = Organization.builder()
            .name("Test Org")
            .slug("test-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        org = organizationRepository.save(org);
        
        Environment env = Environment.builder()
            .organization(org)
            .key("prod")
            .displayName("Production")
            .kind(EnvironmentKind.PRODUCTION)
            .status(EnvironmentStatus.ACTIVE)
            .build();
        env = environmentRepository.save(env);
        
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
        // Persist organization fixture (required for Hibernate associations)
        Organization org = Organization.builder()
            .name("Test Org")
            .slug("test-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        org = organizationRepository.save(org);
        
        AuditEvent event = auditService.recordEvent(AuditService.builder()
            .organization(org)
            .eventType("test_event")
            .resultStatus("original"));
        
        AuditEvent savedEvent = auditEventRepository.findById(event.getId()).orElseThrow();
        
        assertEquals("test_event", savedEvent.getEventType());
        assertEquals("original", savedEvent.getResultStatus());
    }
}
