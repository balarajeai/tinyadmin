package com.tinyadmin.cloud.security;

import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

/**
 * Authenticated operator identity for V1 MVP.
 * Represents a trusted server-side authenticated user session.
 * 
 * Security properties:
 * - actor_id is SERVER-ASSIGNED, never client-supplied
 * - organization access derived from authenticated membership
 * - client organizationId parameters are resource selectors, not authorization proof
 */
@Data
@Builder
public class OperatorAuthenticationPrincipal {
    
    /**
     * Authenticated user ID (server-assigned from session/JWT).
     * This is the authoritative actor_id for operations.
     */
    private UUID userId;
    
    /**
     * User email (authenticated).
     */
    private String email;
    
    /**
     * Organizations this user has access to (from membership table).
     * Used to validate resource selector organization IDs in API requests.
     */
    private Set<UUID> organizationIds;
    
    /**
     * Currently active organization context (selected by user, validated against membership).
     * Used as default organization for operations when not explicitly specified.
     */
    private UUID activeOrganizationId;
    
    /**
     * Checks if user has access to specified organization.
     */
    public boolean hasAccessToOrganization(UUID organizationId) {
        return organizationIds != null && organizationIds.contains(organizationId);
    }
}
