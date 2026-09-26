package com.tinyadmin.cloud.operation;

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
@Table(name = "previews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Preview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column
    private UUID operationId;
    
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
    
    @NotNull
    @Column(nullable = false)
    private Instant requestedAt;
    
    @Column
    private Integer expectedAffectedCount;
    
    @Column(columnDefinition = "TEXT")
    private String expectedAffectedSummary;
    
    @Column(columnDefinition = "TEXT")
    private String limitationFlags;
    
    @Column(columnDefinition = "TEXT")
    private String stalenessHint;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreviewStatus status;
    
    @PrePersist
    protected void onCreate() {
        requestedAt = Instant.now();
    }
}
