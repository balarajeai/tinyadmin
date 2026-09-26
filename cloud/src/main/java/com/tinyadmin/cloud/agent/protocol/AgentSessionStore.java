package com.tinyadmin.cloud.agent.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent session store for validated authenticated Agent sessions.
 * 
 * CRITICAL SECURITY (SEC-PR23-002):
 * - Store is the ONLY source of Agent session authority
 * - Sessions created ONLY after successful challenge-response auth
 * - Session validation checks expiry and Agent revocation status
 * - Client-supplied agent IDs are NEVER trusted
 * 
 * V1 MVP: In-memory store
 * Production: Replace with distributed session store (Redis, database)
 */
@Component
@Slf4j
public class AgentSessionStore {
    
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final AgentSessionAuthService authService;
    
    public AgentSessionStore(AgentSessionAuthService authService) {
        this.authService = authService;
    }
    
    /**
     * Stores authenticated session after successful challenge-response.
     * ONLY call after Agent auth is verified.
     */
    public void storeSession(AgentSession session) {
        if (!session.isAuthenticated()) {
            throw new IllegalArgumentException("Cannot store unauthenticated session");
        }
        
        sessions.put(session.getSessionId(), session);
        log.info("Stored authenticated session: sessionId={} agentId={} org={} env={}", 
                session.getSessionId(), session.getAgentId(), 
                session.getOrganizationId(), session.getEnvironmentId());
    }
    
    /**
     * Retrieves and validates session.
     * 
     * CRITICAL: This is the authoritative source of Agent identity.
     * Returns empty if:
     * - Session ID not found
     * - Session expired
     * - Agent revoked
     * 
     * @param sessionId Session ID from client
     * @return Validated session if valid; empty otherwise
     */
    public Optional<AgentSession> getValidatedSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return Optional.empty();
        }
        
        // Validate session is still valid (not expired, Agent not revoked)
        if (!authService.isSessionValid(session)) {
            log.warn("Session invalid or expired: {}", sessionId);
            sessions.remove(sessionId); // Clean up
            return Optional.empty();
        }
        
        return Optional.of(session);
    }
    
    /**
     * Removes session (logout or revocation).
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Removed session: {}", sessionId);
    }
    
    /**
     * Periodic cleanup of expired sessions.
     * V1: Manual cleanup; Production: scheduled task or TTL-based eviction
     */
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().getSessionExp())) {
                log.debug("Removing expired session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
