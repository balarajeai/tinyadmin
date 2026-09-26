package com.tinyadmin.cloud.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * V1 MVP Authentication Provider (Blocker #2 - Operator Auth).
 * 
 * Returns OperatorAuthenticationPrincipal with server-validated organization memberships.
 * 
 * CRITICAL SECURITY PROPERTIES:
 * - actor_id is SERVER-ASSIGNED from authenticated identity
 * - organization memberships SERVER-VALIDATED (not client-supplied)
 * - Empty/missing membership is fail-closed (rejected)
 * - Demo credentials marked as test/demo only
 * 
 * V1 implementation: Hardcoded test user for MVP
 * Production: Replace with database-backed membership lookup or OIDC/SSO
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class OperatorAuthenticationProvider implements AuthenticationProvider {
    
    private final PasswordEncoder passwordEncoder;
    
    // Test Organization ID from V99__test_data.sql
    private static final UUID TEST_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    // Demo user credentials (DEMO ONLY - not for production)
    private static final String DEMO_EMAIL = "admin@tinyadmin.test";
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();
        
        // V1 MVP: Demo user hardcoded
        // Production: Query user table + membership table
        if (!DEMO_EMAIL.equals(email)) {
            log.warn("Authentication failed: unknown user {}", email);
            throw new BadCredentialsException("Invalid credentials");
        }
        
        // Verify password
        if (!passwordEncoder.matches(password, passwordEncoder.encode("admin"))) {
            log.warn("Authentication failed: invalid password for user {}", email);
            throw new BadCredentialsException("Invalid credentials");
        }
        
        // CRITICAL: Construct OperatorAuthenticationPrincipal with SERVER-VALIDATED memberships
        // V1: Demo user has access to test organization
        // Production: Query membership table for real org access
        OperatorAuthenticationPrincipal principal = OperatorAuthenticationPrincipal.builder()
                .userId(UUID.nameUUIDFromBytes(email.getBytes()))
                .email(email)
                .organizationIds(Set.of(TEST_ORG_ID))
                .activeOrganizationId(TEST_ORG_ID)
                .build();
        
        log.info("Operator authenticated: user_id={} email={} orgs={} (DEMO CREDENTIALS)", 
                principal.getUserId(), principal.getEmail(), principal.getOrganizationIds());
        
        return new UsernamePasswordAuthenticationToken(
                principal,
                password,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
        );
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
