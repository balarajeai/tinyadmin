package com.tinyadmin.cloud.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * V1 MVP operator authentication service.
 * 
 * Provides authenticated operator identity from Spring Security context.
 * For V1: simplified session-based auth (not SSO/OIDC).
 * 
 * CRITICAL: actor_id is NEVER accepted from client input.
 * Organization access is ALWAYS validated against authenticated membership.
 */
@Service
@Slf4j
public class OperatorAuthenticationService {
    
    /**
     * Gets authenticated operator from security context.
     * 
     * @return Authenticated operator principal, or empty if not authenticated
     */
    public Optional<OperatorAuthenticationPrincipal> getAuthenticatedOperator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof OperatorAuthenticationPrincipal) {
            return Optional.of((OperatorAuthenticationPrincipal) principal);
        }
        
        // For V1 test fixtures: allow string principal (username/email)
        // Production would require full authentication provider
        if (principal instanceof String) {
            String email = (String) principal;
            // Test fixture: create minimal principal
            // Real implementation would query user/membership database
            return Optional.of(OperatorAuthenticationPrincipal.builder()
                    .userId(UUID.nameUUIDFromBytes(email.getBytes()))
                    .email(email)
                    .organizationIds(Set.of()) // Empty for unauthenticated test case
                    .build());
        }
        
        return Optional.empty();
    }
    
    /**
     * Requires authenticated operator or throws SecurityException.
     */
    public OperatorAuthenticationPrincipal requireAuthenticated() {
        return getAuthenticatedOperator()
                .orElseThrow(() -> new SecurityException("Operator authentication required"));
    }
    
    /**
     * Validates operator has access to specified organization.
     * Throws SecurityException if no access.
     */
    public void requireOrganizationAccess(UUID organizationId) {
        OperatorAuthenticationPrincipal operator = requireAuthenticated();
        
        if (!operator.hasAccessToOrganization(organizationId)) {
            log.warn("Operator {} attempted access to unauthorized organization {}", 
                     operator.getUserId(), organizationId);
            throw new SecurityException("Access denied to organization " + organizationId);
        }
    }
    
    /**
     * Gets authoritative actor_id for operation attribution.
     * This is SERVER-ASSIGNED from authenticated session.
     * Client-supplied actor_id is FORBIDDEN.
     */
    public UUID getAuthenticatedActorId() {
        return requireAuthenticated().getUserId();
    }
}
