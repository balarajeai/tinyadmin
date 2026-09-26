package com.tinyadmin.cloud.action;

import com.tinyadmin.cloud.organization.Organization;
import com.tinyadmin.cloud.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Action bootstrap service (Blocker #8 - Production Action Bootstrap).
 * 
 * Provides mechanism to create standard Actions (e.g., Unlock User)
 * for production organizations without relying on V99 test data.
 * 
 * Actions are org-scoped; each org gets their own Action instances.
 * Test org uses fixed UUID matching Agent PR #24.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActionBootstrapService {
    
    /**
     * Shared Unlock User Action UUID for test organization.
     * Matches Agent PR #24 e3a006885cb79f0123da8404feb4c7cf4b81272f constant.
     * Seeded in V99__test_data.sql for integration tests.
     * Production orgs get dynamically generated UUIDs.
     */
    public static final UUID TEST_UNLOCK_USER_ACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final OrganizationRepository organizationRepository;
    
    /**
     * Bootstrap standard Actions for an organization.
     * Idempotent: safe to call multiple times.
     * 
     * Creates:
     * - Unlock User Action (V1 vertical slice)
     */
    @Transactional
    public void bootstrapStandardActions(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));
        
        ensureUnlockUserAction(org);
        
        log.info("Standard Actions bootstrapped for organization {}", organizationId);
    }
    
    /**
     * Ensures Unlock User Action exists for organization.
     * Finding #10: Production orgs need this Action without V99 test coupling.
     */
    private void ensureUnlockUserAction(Organization org) {
        // Check if org already has Unlock User action
        Optional<ActionDefinition> existing = actionDefinitionRepository
                .findByOrganizationIdAndKey(org.getId(), "unlock-user-v1");
        
        if (existing.isPresent()) {
            log.debug("Unlock User Action already exists for org {}", org.getId());
            return;
        }
        
        // Create Unlock User action
        ActionDefinition action = ActionDefinition.builder()
                .organization(org)
                .key("unlock-user-v1")
                .name("Unlock User")
                .description("Unlock a locked user account")
                .status(ActionStatus.ENABLED)
                .requiresConfirmation(true)
                .productionExtraConfirm(true)
                .maxAffectedRecords(1)
                .rollbackPolicy(RollbackPolicy.NONE)
                .build();
        
        actionDefinitionRepository.save(action);
        
        log.info("Created Unlock User Action for organization {}", org.getId());
    }
}
