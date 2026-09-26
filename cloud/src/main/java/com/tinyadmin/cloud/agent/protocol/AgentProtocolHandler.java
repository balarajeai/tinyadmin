package com.tinyadmin.cloud.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.agent.protocol.messages.*;
import com.tinyadmin.cloud.operation.Operation;
import com.tinyadmin.cloud.operation.OperationLifecycleStatus;
import com.tinyadmin.cloud.operation.OperationRepository;
import com.tinyadmin.cloud.security.CommandSigningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for Agent↔Cloud protocol (Issue #3 / ADR 0007).
 * 
 * Implements:
 * - §5 Session lifecycle with challenge-response authentication
 * - §7 Result acknowledgment with authentication
 * - §8 Reconnect with cancel/revoke sync
 * 
 * Security properties:
 * - P-OUTBOUND: Agent-initiated only
 * - P-AGENT-ID: Per-agent Ed25519 identity
 * - SR-AGENT-AUTH-*: Challenge-response; fail closed
 */
@Component
@Slf4j
public class AgentProtocolHandler extends TextWebSocketHandler {
    
    private final AgentSessionAuthService sessionAuthService;
    private final ResultAckService resultAckService;
    private final CommandSigningService commandSigningService;
    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;
    
    // WebSocket session ID → Agent session mapping
    private final Map<String, AgentSession> activeSessions = new ConcurrentHashMap<>();
    
    // Protocol version (V1)
    private static final int CURRENT_PROTOCOL_VERSION = 1;
    
    public AgentProtocolHandler(
            AgentSessionAuthService sessionAuthService,
            ResultAckService resultAckService,
            CommandSigningService commandSigningService,
            OperationRepository operationRepository,
            ObjectMapper objectMapper) {
        this.sessionAuthService = sessionAuthService;
        this.resultAckService = resultAckService;
        this.commandSigningService = commandSigningService;
        this.operationRepository = operationRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: sessionId={}", session.getId());
        // Agent will send session_hello next
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.debug("Received message: sessionId={} payload={}", session.getId(), payload);
            
            // Parse message using polymorphic deserialization
            AgentMessage agentMessage = objectMapper.readValue(payload, AgentMessage.class);
            
            // Route by message type
            if (agentMessage instanceof SessionHelloMessage) {
                handleSessionHello(session, (SessionHelloMessage) agentMessage);
            } else if (agentMessage instanceof ChallengeResponseMessage) {
                handleChallengeResponse(session, (ChallengeResponseMessage) agentMessage);
            } else if (agentMessage instanceof ResultReportMessage) {
                handleResultReport(session, (ResultReportMessage) agentMessage);
            } else {
                log.warn("Unknown message type: {}", agentMessage.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            log.error("Error handling message: sessionId={}", session.getId(), e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeEx) {
                log.error("Error closing session: sessionId={}", session.getId(), closeEx);
            }
        }
    }
    
    /**
     * Handle session_hello: validate Agent identity and issue challenge.
     */
    private void handleSessionHello(WebSocketSession session, SessionHelloMessage hello) throws Exception {
        UUID agentId = hello.getAgentId();
        int protocolVersion = hello.getProtocolVersion() != null ? hello.getProtocolVersion() : 1;
        
        log.info("Session hello: sessionId={} agentId={} protocolVersion={}", 
                 session.getId(), agentId, protocolVersion);
        
        // Validate protocol version
        if (protocolVersion != CURRENT_PROTOCOL_VERSION) {
            log.warn("Unsupported protocol version: sessionId={} version={}", 
                     session.getId(), protocolVersion);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unsupported protocol version"));
            return;
        }
        
        // Validate Agent identity (check exists, active, not revoked)
        Optional<Agent> agentOpt = sessionAuthService.validateAgentIdentity(agentId);
        if (agentOpt.isEmpty()) {
            log.warn("Agent authentication failed: invalid or revoked agentId={}", agentId);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Agent revoked or invalid"));
            return;
        }
        
        Agent agent = agentOpt.get();
        
        // Generate challenge nonce
        byte[] challengeNonce = sessionAuthService.generateChallenge();
        String nonceBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeNonce);
        
        // Store pending challenge in session (temporary unauthenticated session)
        AgentSession pendingSession = AgentSession.builder()
                .sessionId(session.getId())
                .agentId(agent.getId())
                .organizationId(agent.getOrganization().getId())
                .environmentId(agent.getEnvironment().getId())
                .authenticated(false)
                .pendingChallengeNonce(challengeNonce)
                .protocolVersion(protocolVersion)
                .build();
        
        activeSessions.put(session.getId(), pendingSession);
        
        // Send challenge to Agent
        ChallengeMessage challenge = ChallengeMessage.builder()
                .nonce(nonceBase64Url)
                .build();
        challenge.setMessageType("challenge");
        challenge.setProtocolVersion(CURRENT_PROTOCOL_VERSION);
        
        String challengeJson = objectMapper.writeValueAsString(challenge);
        session.sendMessage(new TextMessage(challengeJson));
        
        log.debug("Challenge sent: sessionId={} agentId={}", session.getId(), agentId);
    }
    
    /**
     * Handle challenge_response: verify signature and complete authentication.
     */
    private void handleChallengeResponse(WebSocketSession session, ChallengeResponseMessage response) 
            throws Exception {
        
        AgentSession pendingSession = activeSessions.get(session.getId());
        if (pendingSession == null || pendingSession.isAuthenticated()) {
            log.warn("No pending challenge: sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        byte[] challengeNonce = pendingSession.getPendingChallengeNonce();
        if (challengeNonce == null) {
            log.warn("No challenge nonce: sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        // Get Agent for verification
        Optional<Agent> agentOpt = sessionAuthService.validateAgentIdentity(pendingSession.getAgentId());
        if (agentOpt.isEmpty()) {
            log.warn("Agent no longer valid: agentId={}", pendingSession.getAgentId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Agent revoked"));
            activeSessions.remove(session.getId());
            return;
        }
        
        Agent agent = agentOpt.get();
        
        // Verify challenge response signature
        boolean valid = sessionAuthService.verifyChallengeResponse(
                challengeNonce, 
                response.getSignature(), 
                agent
        );
        
        if (!valid) {
            log.warn("Challenge response verification failed: agentId={}", agent.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid signature"));
            activeSessions.remove(session.getId());
            return;
        }
        
        // Authentication successful - create authenticated session
        AgentSession authenticatedSession = sessionAuthService.createAuthenticatedSession(
                agent, 
                pendingSession.getProtocolVersion()
        );
        authenticatedSession.setSessionId(session.getId());
        activeSessions.put(session.getId(), authenticatedSession);
        
        log.info("Agent authenticated: sessionId={} agentId={} orgId={} envId={}", 
                 session.getId(), agent.getId(), agent.getOrganization().getId(), agent.getEnvironment().getId());
        
        // Send session_ok with session details
        SessionOkMessage sessionOk = SessionOkMessage.builder()
                .sessionExp(authenticatedSession.getSessionExp().getEpochSecond())
                .serverTime(Instant.now().getEpochSecond())
                .cloudCommandSigningPubkey(Base64.getEncoder().encodeToString(
                        commandSigningService.getPublicKey().getEncoded()
                ))
                .build();
        sessionOk.setMessageType("session_ok");
        sessionOk.setProtocolVersion(CURRENT_PROTOCOL_VERSION);
        
        String sessionOkJson = objectMapper.writeValueAsString(sessionOk);
        session.sendMessage(new TextMessage(sessionOkJson));
        
        // Send cancel/revoke sync (CR-PR12-001 §8)
        sendCancelRevokeSync(session, authenticatedSession);
        
        log.debug("Session established: sessionId={} agentId={}", session.getId(), agent.getId());
    }
    
    /**
     * Send authoritative cancel/revoke sync after authentication (CR-PR12-001).
     * Agent MUST apply this state before executing any pending mutations.
     * 
     * CRITICAL (Finding #4): FAILED != CANCELLED
     * - FAILED: Mutation attempted and failed definitively
     * - CANCELLED: Operation cancelled before execution (revoked/disabled/rejected)
     */
    private void sendCancelRevokeSync(WebSocketSession session, AgentSession agentSession) throws Exception {
        // Query CANCELLED operations (NOT FAILED) for this Agent
        // Finding #4: FAILED != CANCELLED
        List<UUID> canceledOperationIds = operationRepository
                .findByAgentIdAndLifecycleStatus(agentSession.getAgentId(), OperationLifecycleStatus.CANCELLED)
                .stream()
                .map(Operation::getId)
                .toList();
        
        CancelRevokeSyncMessage syncMessage = CancelRevokeSyncMessage.builder()
                .agentRevoked(false) // Agent is authenticated, so not revoked
                .canceledOperationIds(canceledOperationIds)
                .authzEpoch(Instant.now().toEpochMilli()) // Simple epoch marker
                .build();
        syncMessage.setMessageType("cancel_revoke_sync");
        syncMessage.setProtocolVersion(CURRENT_PROTOCOL_VERSION);
        
        String syncJson = objectMapper.writeValueAsString(syncMessage);
        session.sendMessage(new TextMessage(syncJson));
        
        log.debug("Cancel/revoke sync sent: sessionId={} agentId={} canceledCount={}", 
                  session.getId(), agentSession.getAgentId(), canceledOperationIds.size());
    }
    
    /**
     * Handle result_report: persist result and send authenticated ack (§7 / SEC-PR12-005).
     */
    private void handleResultReport(WebSocketSession session, ResultReportMessage report) throws Exception {
        AgentSession agentSession = activeSessions.get(session.getId());
        
        // Require authenticated session
        if (agentSession == null || !agentSession.isAuthenticated()) {
            log.warn("Result report from unauthenticated session: sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        // Validate session still active
        if (!sessionAuthService.isSessionValid(agentSession)) {
            log.warn("Result report from invalid session: sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Session expired or revoked"));
            activeSessions.remove(session.getId());
            return;
        }
        
        UUID operationId = report.getOperationId();
        String status = report.getStatus();
        
        log.info("Result report received: sessionId={} agentId={} operationId={} status={}", 
                 session.getId(), agentSession.getAgentId(), operationId, status);
        
        // Update operation lifecycle (idempotent)
        Optional<Operation> operationOpt = operationRepository.findById(operationId);
        if (operationOpt.isEmpty()) {
            log.warn("Result report for unknown operation: operationId={}", operationId);
            // Still send ack to allow Agent to clear its durable result
        } else {
            Operation operation = operationOpt.get();
            
            // Verify operation belongs to this Agent
            if (operation.getAgent() == null || !operation.getAgent().getId().equals(agentSession.getAgentId())) {
                log.error("Result report for wrong agent: operationId={} expectedAgent={} actualAgent={}", 
                          operationId, operation.getAgent() != null ? operation.getAgent().getId() : null, agentSession.getAgentId());
                return;
            }
            
            // Update operation status (idempotent - already succeeded/failed operations unchanged)
            OperationLifecycleStatus newStatus = mapResultStatus(status);
            if (newStatus != null && operation.getLifecycleStatus() == OperationLifecycleStatus.PENDING_RESULT) {
                operation.setLifecycleStatus(newStatus);
                operation.setUpdatedAt(Instant.now());
                operationRepository.save(operation);
                
                log.info("Operation status updated: operationId={} status={}", operationId, newStatus);
            }
        }
        
        // Create authenticated result_ack (SEC-PR12-005)
        Map<String, String> ackData = resultAckService.createAuthenticatedAck(operationId);
        
        ResultAckMessage ack = ResultAckMessage.builder()
                .operationId(operationId)
                .ackSignature(ackData.get("ack_signature"))
                .ackPayloadDigest(ackData.get("ack_payload_digest"))
                .build();
        ack.setMessageType("result_ack");
        ack.setProtocolVersion(CURRENT_PROTOCOL_VERSION);
        
        String ackJson = objectMapper.writeValueAsString(ack);
        session.sendMessage(new TextMessage(ackJson));
        
        log.debug("Result ack sent: sessionId={} operationId={}", session.getId(), operationId);
    }
    
    private OperationLifecycleStatus mapResultStatus(String status) {
        return switch (status) {
            case "succeeded" -> OperationLifecycleStatus.SUCCEEDED;
            case "failed" -> OperationLifecycleStatus.FAILED;
            case "unknown" -> OperationLifecycleStatus.UNKNOWN;
            default -> null;
        };
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: sessionId={} status={}", session.getId(), status);
        activeSessions.remove(session.getId());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        activeSessions.remove(session.getId());
    }
}
