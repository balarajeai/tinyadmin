package com.tinyadmin.cloud.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {
    Optional<Agent> findByIdAndOrganizationIdAndEnvironmentId(
        UUID id, UUID organizationId, UUID environmentId
    );
}
