package com.tinyadmin.cloud.action;

import com.tinyadmin.cloud.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_definitions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_action_definitions_org_key", columnNames = {"organization_id", "key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;
    
    @NotBlank
    @Column(nullable = false)
    private String key;
    
    @NotBlank
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;
    
    @NotNull
    @Column(nullable = false)
    private Boolean requiresConfirmation;
    
    @NotNull
    @Column(nullable = false)
    private Boolean productionExtraConfirm;
    
    @Column
    private Integer maxAffectedRecords;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RollbackPolicy rollbackPolicy;
    
    @Column(columnDefinition = "TEXT")
    private String effectDefinition;
    
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
