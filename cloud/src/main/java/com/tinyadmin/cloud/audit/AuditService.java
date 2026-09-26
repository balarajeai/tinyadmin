package com.tinyadmin.cloud.audit;

import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.organization.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditEventRepository auditEventRepository;
    
    @Transactional
    public AuditEvent recordEvent(AuditEventBuilder builder) {
        AuditEvent event = AuditEvent.builder()
            .organization(builder.organization)
            .environment(builder.environment)
            .actorUserId(builder.actorUserId)
            .agentId(builder.agentId)
            .connectionId(builder.connectionId)
            .operationId(builder.operationId)
            .eventType(builder.eventType)
            .actionDefinitionId(builder.actionDefinitionId)
            .target(builder.target)
            .beforeState(builder.beforeState)
            .afterState(builder.afterState)
            .resultStatus(builder.resultStatus)
            .rollbackOfOperationId(builder.rollbackOfOperationId)
            .securityContext(builder.securityContext)
            .build();
        
        AuditEvent saved = auditEventRepository.save(event);
        log.info("Audit event recorded: type={}, operationId={}, eventId={}", 
            event.getEventType(), event.getOperationId(), saved.getId());
        
        return saved;
    }
    
    public List<AuditEvent> getOperationAuditTrail(UUID operationId) {
        return auditEventRepository.findByOperationIdOrderByOccurredAtAsc(operationId);
    }
    
    public List<AuditEvent> getOrganizationAuditTrail(UUID organizationId) {
        return auditEventRepository.findByOrganizationIdOrderByOccurredAtDesc(organizationId);
    }
    
    public static class AuditEventBuilder {
        private Organization organization;
        private Environment environment;
        private UUID actorUserId;
        private UUID agentId;
        private UUID connectionId;
        private UUID operationId;
        private String eventType;
        private UUID actionDefinitionId;
        private String target;
        private String beforeState;
        private String afterState;
        private String resultStatus;
        private UUID rollbackOfOperationId;
        private String securityContext;
        
        public AuditEventBuilder organization(Organization organization) {
            this.organization = organization;
            return this;
        }
        
        public AuditEventBuilder environment(Environment environment) {
            this.environment = environment;
            return this;
        }
        
        public AuditEventBuilder actorUserId(UUID actorUserId) {
            this.actorUserId = actorUserId;
            return this;
        }
        
        public AuditEventBuilder agentId(UUID agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public AuditEventBuilder connectionId(UUID connectionId) {
            this.connectionId = connectionId;
            return this;
        }
        
        public AuditEventBuilder operationId(UUID operationId) {
            this.operationId = operationId;
            return this;
        }
        
        public AuditEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public AuditEventBuilder actionDefinitionId(UUID actionDefinitionId) {
            this.actionDefinitionId = actionDefinitionId;
            return this;
        }
        
        public AuditEventBuilder target(String target) {
            this.target = target;
            return this;
        }
        
        public AuditEventBuilder beforeState(String beforeState) {
            this.beforeState = beforeState;
            return this;
        }
        
        public AuditEventBuilder afterState(String afterState) {
            this.afterState = afterState;
            return this;
        }
        
        public AuditEventBuilder resultStatus(String resultStatus) {
            this.resultStatus = resultStatus;
            return this;
        }
        
        public AuditEventBuilder rollbackOfOperationId(UUID rollbackOfOperationId) {
            this.rollbackOfOperationId = rollbackOfOperationId;
            return this;
        }
        
        public AuditEventBuilder securityContext(String securityContext) {
            this.securityContext = securityContext;
            return this;
        }
    }
    
    public static AuditEventBuilder builder() {
        return new AuditEventBuilder();
    }
}
