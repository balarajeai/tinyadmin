package com.tinyadmin.cloud.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionDefinitionRepository extends JpaRepository<ActionDefinition, UUID> {
    Optional<ActionDefinition> findByOrganizationIdAndKey(UUID organizationId, String key);
}
