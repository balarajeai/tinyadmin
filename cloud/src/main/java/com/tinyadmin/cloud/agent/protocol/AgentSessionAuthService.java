package com.tinyadmin.cloud.agent.protocol;

import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.agent.AgentRepository;
import com.tinyadmin.cloud.agent.AgentStatus;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent session authentication service implementing challenge-response protocol (Issue #3 §5).
 * 
 * Security properties enforced:
 * - P-AGENT-ID: Unique agent_id with enrolled Ed25519 public key
 * - SR-AGENT-AUTH-01/02/03: Challenge-response; revoked agents rejected
 * - Fail closed on verification failures
 */
@Service
@Slf4j
public class AgentSessionAuthService {
    
    private final AgentRepository agentRepository;
    private final SecureRandom secureRandom;
    
    // Session validity period (5 minutes per short-lived policy class)
    private static final long SESSION_TTL_SECONDS = 300;
    
    public AgentSessionAuthService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
        this.secureRandom = new SecureRandom();
        
        // Register BouncyCastle for consistent Ed25519 handling
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    /**
     * Generates fresh cryptographic challenge for Agent authentication.
     * Nonce is 32 bytes (256 bits) for Ed25519 security level.
     */
    public byte[] generateChallenge() {
        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        return nonce;
    }
    
    /**
     * Validates Agent session hello and returns Agent identity if valid.
     * Checks Agent exists, is ACTIVE, and not revoked.
     * 
     * @return Optional<Agent> if valid and not revoked; empty if invalid/revoked
     */
    public Optional<Agent> validateAgentIdentity(UUID agentId) {
        Optional<Agent> agentOpt = agentRepository.findById(agentId);
        
        if (agentOpt.isEmpty()) {
            log.warn("Agent authentication failed: unknown agent_id={}", agentId);
            return Optional.empty();
        }
        
        Agent agent = agentOpt.get();
        
        // SR-AGENT-AUTH-03: Reject revoked agents
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            log.warn("Agent authentication failed: agent_id={} status={} (not ACTIVE)", 
                     agentId, agent.getStatus());
            return Optional.empty();
        }
        
        return Optional.of(agent);
    }
    
    /**
     * Verifies Agent's challenge response signature.
     * Agent must sign the challenge nonce with enrolled Ed25519 private key.
     * 
     * Security: SR-AGENT-AUTH-01 challenge-response
     * 
     * @param challengeNonce The original nonce sent to Agent
     * @param signatureBase64Url Agent's signature (base64url-encoded)
     * @param agent Agent entity with enrolled public key
     * @return true if signature is valid; false otherwise
     */
    public boolean verifyChallengeResponse(
            byte[] challengeNonce, 
            String signatureBase64Url, 
            Agent agent) {
        
        try {
            // Decode Agent's signature
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureBase64Url);
            
            // Get Agent's enrolled public key (stored as X.509-encoded base64)
            String publicKeyMaterial = agent.getPublicKeyMaterial();
            if (publicKeyMaterial == null || publicKeyMaterial.isBlank()) {
                log.error("Agent {} has no enrolled public key", agent.getId());
                return false;
            }
            
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyMaterial);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            
            // Verify signature over challenge nonce
            Signature signature = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(publicKey);
            signature.update(challengeNonce);
            
            boolean valid = signature.verify(signatureBytes);
            
            if (!valid) {
                log.warn("Agent {} challenge response signature invalid", agent.getId());
            }
            
            return valid;
            
        } catch (Exception e) {
            log.error("Agent {} challenge response verification failed", agent.getId(), e);
            return false;
        }
    }
    
    /**
     * Creates authenticated session after successful challenge-response.
     * 
     * @param agent Authenticated agent
     * @param protocolVersion Negotiated protocol version
     * @return AgentSession with expiry and bindings
     */
    public AgentSession createAuthenticatedSession(Agent agent, int protocolVersion) {
        Instant now = Instant.now();
        Instant sessionExp = now.plusSeconds(SESSION_TTL_SECONDS);
        
        return AgentSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .agentId(agent.getId())
                .organizationId(agent.getOrganization().getId())
                .environmentId(agent.getEnvironment().getId())
                .sessionExp(sessionExp)
                .establishedAt(now)
                .authenticated(true)
                .protocolVersion(protocolVersion)
                .build();
    }
    
    /**
     * Checks if session is still valid (not expired, Agent not revoked).
     * 
     * @param session Session to validate
     * @return true if session is valid and Agent is still active
     */
    public boolean isSessionValid(AgentSession session) {
        if (!session.isAuthenticated()) {
            return false;
        }
        
        if (Instant.now().isAfter(session.getSessionExp())) {
            log.debug("Session {} expired", session.getSessionId());
            return false;
        }
        
        // Check Agent is still active (not revoked)
        Optional<Agent> agentOpt = agentRepository.findById(session.getAgentId());
        if (agentOpt.isEmpty() || agentOpt.get().getStatus() != AgentStatus.ACTIVE) {
            log.warn("Session {} invalid: Agent {} revoked or missing", 
                     session.getSessionId(), session.getAgentId());
            return false;
        }
        
        return true;
    }
}
