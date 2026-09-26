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
     * CRITICAL SECURITY (Blocker #2):
     * - Principal MUST be OperatorAuthenticationPrincipal with server-validated memberships
     * - String principal (UserDetails username) is REJECTED fail-closed
     * - Empty membership is REJECTED fail-closed
     * - Client cannot spoof actor_id or organization membership
     * 
     * @return Authenticated operator principal, or empty if not authenticated
     */
    public Optional<OperatorAuthenticationPrincipal> getAuthenticatedOperator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authentication in security context");
            return Optional.empty();
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof OperatorAuthenticationPrincipal operatorPrincipal) {
            // CRITICAL: Verify principal has valid organization memberships
            if (operatorPrincipal.getOrganizationIds() == null || operatorPrincipal.getOrganizationIds().isEmpty()) {
                log.error("SECURITY: OperatorAuthenticationPrincipal {} has no organization memberships (fail-closed)", 
                         operatorPrincipal.getUserId());
                return Optional.empty();
            }
            return Optional.of(operatorPrincipal);
        }
        
        // CRITICAL SECURITY: String principal (UserDetails username) is REJECTED
        // Production auth MUST use AuthenticationProvider that returns OperatorAuthenticationPrincipal
        // with server-validated organization memberships
        log.error("SECURITY: Invalid principal type {}. Expected OperatorAuthenticationPrincipal with validated memberships", 
                 principal.getClass().getName());
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
