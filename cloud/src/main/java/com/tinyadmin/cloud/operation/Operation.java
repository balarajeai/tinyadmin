package com.tinyadmin.cloud.operation;

import com.tinyadmin.cloud.action.ActionDefinition;
import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.connection.Connection;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operations", indexes = {
    @Index(name = "idx_operations_org_env_created", columnList = "organization_id,environment_id,created_at"),
    @Index(name = "idx_operations_lifecycle_updated", columnList = "lifecycle_status,updated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private Connection connection;
    
    @Column
    private UUID actorUserId;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationKind kind;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_definition_id")
    private ActionDefinition actionDefinition;
    
    @Column(columnDefinition = "TEXT")
    private String target;
    
    @Column(columnDefinition = "TEXT")
    private String parameters;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationLifecycleStatus lifecycleStatus;
    
    @Column
    private UUID previewId;
    
    @Column(length = 64)
    private String previewFingerprint;
    
    @Column
    private UUID confirmationId;
    
    @Column
    private UUID parentOperationId;
    
    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @NotNull
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Column
    private Instant terminalAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
