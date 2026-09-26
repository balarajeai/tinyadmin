package com.tinyadmin.cloud.environment;

import com.tinyadmin.cloud.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_environments_org_key", columnNames = {"organization_id", "key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;
    
    @NotBlank
    @Column(nullable = false, updatable = false)
    private String key;
    
    @NotBlank
    @Column(nullable = false)
    private String displayName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentKind kind;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentStatus status;
    
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
