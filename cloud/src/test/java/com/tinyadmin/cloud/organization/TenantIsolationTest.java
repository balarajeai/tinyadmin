package com.tinyadmin.cloud.organization;

import com.tinyadmin.cloud.BaseIntegrationTest;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.environment.EnvironmentKind;
import com.tinyadmin.cloud.environment.EnvironmentRepository;
import com.tinyadmin.cloud.environment.EnvironmentStatus;
import com.tinyadmin.cloud.operation.Operation;
import com.tinyadmin.cloud.operation.OperationKind;
import com.tinyadmin.cloud.operation.OperationLifecycleStatus;
import com.tinyadmin.cloud.operation.OperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantIsolationTest extends BaseIntegrationTest {
    
    @Autowired
    private OrganizationRepository organizationRepository;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private OperationRepository operationRepository;
    
    @Test
    void shouldIsolateOperationsByOrganization() {
        Organization org1 = Organization.builder()
            .name("Org 1")
            .slug("org-1-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        org1 = organizationRepository.save(org1);
        
        Organization org2 = Organization.builder()
            .name("Org 2")
            .slug("org-2-" + UUID.randomUUID())
            .status(OrganizationStatus.ACTIVE)
            .build();
        org2 = organizationRepository.save(org2);
        
        Environment env1 = Environment.builder()
            .organization(org1)
            .key("prod")
            .displayName("Production")
            .kind(EnvironmentKind.PRODUCTION)
            .status(EnvironmentStatus.ACTIVE)
            .build();
        env1 = environmentRepository.save(env1);
        
        Operation op1 = Operation.builder()
            .organization(org1)
            .environment(env1)
            .kind(OperationKind.PREVIEW)
            .lifecycleStatus(OperationLifecycleStatus.PREVIEW_PENDING)
            .build();
        op1 = operationRepository.save(op1);
        
        assertTrue(operationRepository.findByIdAndOrganizationId(op1.getId(), org1.getId()).isPresent(),
            "Operation should be found for org1");
        
        assertTrue(operationRepository.findByIdAndOrganizationId(op1.getId(), org2.getId()).isEmpty(),
            "Operation should NOT be found when queried with wrong organization");
    }
    
    @Test
    void shouldEnforceUniqueOrganizationSlug() {
        String slug = "unique-slug-" + UUID.randomUUID();
        
        Organization org1 = Organization.builder()
            .name("Org 1")
            .slug(slug)
            .status(OrganizationStatus.ACTIVE)
            .build();
        organizationRepository.save(org1);
        
        Organization org2 = Organization.builder()
            .name("Org 2")
            .slug(slug)
            .status(OrganizationStatus.ACTIVE)
            .build();
        
        assertThrows(Exception.class, () -> organizationRepository.saveAndFlush(org2),
            "Should not allow duplicate organization slug");
    }
}
