package com.tinyadmin.cloud.operation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperationRepository extends JpaRepository<Operation, UUID> {
    Optional<Operation> findByIdAndOrganizationId(UUID id, UUID organizationId);
    
    /**
     * Find operations by agent and lifecycle status.
     * Used for cancel/revoke sync to identify operations Agent should discard.
     */
    List<Operation> findByAgentIdAndLifecycleStatus(UUID agentId, OperationLifecycleStatus status);
}
