package com.tinyadmin.cloud.operation;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "confirmations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Confirmation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @Column(nullable = false)
    private UUID operationId;
    
    @Column
    private UUID actorUserId;
    
    @NotNull
    @Column(nullable = false)
    private Instant confirmedAt;
    
    @Column(columnDefinition = "TEXT")
    private String acknowledgedLimitationFlags;
    
    @Column
    private Boolean productionAck;
    
    @Column(columnDefinition = "TEXT")
    private String previewFingerprint;
    
    @PrePersist
    protected void onCreate() {
        confirmedAt = Instant.now();
    }
}
