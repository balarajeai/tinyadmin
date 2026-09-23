package com.tinyadmin.cloud.audit;

import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_events_org_occurred", columnList = "organization_id,occurred_at"),
    @Index(name = "idx_audit_events_operation", columnList = "operation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", updatable = false)
    private Environment environment;
    
    @Column(updatable = false)
    private UUID actorUserId;
    
    @Column(updatable = false)
    private UUID agentId;
    
    @Column(updatable = false)
    private UUID connectionId;
    
    @Column(updatable = false)
    private UUID operationId;
    
    @NotBlank
    @Column(nullable = false, updatable = false)
    private String eventType;
    
    @Column(updatable = false)
    private UUID actionDefinitionId;
    
    @Column(columnDefinition = "TEXT", updatable = false)
    private String target;
    
    @Column(columnDefinition = "TEXT", updatable = false)
    private String beforeState;
    
    @Column(columnDefinition = "TEXT", updatable = false)
    private String afterState;
    
    @Column(updatable = false)
    private String resultStatus;
    
    @Column(updatable = false)
    private UUID rollbackOfOperationId;
    
    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
    
    @Column(columnDefinition = "TEXT", updatable = false)
    private String securityContext;
    
    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
    }
}
