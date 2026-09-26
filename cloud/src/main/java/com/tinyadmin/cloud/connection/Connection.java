package com.tinyadmin.cloud.connection;

import com.tinyadmin.cloud.agent.Agent;
import com.tinyadmin.cloud.environment.Environment;
import com.tinyadmin.cloud.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connections", uniqueConstraints = {
    @UniqueConstraint(name = "uk_connections_org_env_name", columnNames = {"organization_id", "environment_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Connection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;
    
    @NotBlank
    @Column(nullable = false)
    private String name;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseEngine dbEngine;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column
    private String customerSecretRef;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;
    
    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @NotNull
    @Column(nullable = false)
    private Instant updatedAt;
    
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
