package com.tinyadmin.cloud.environment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    Optional<Environment> findByOrganizationIdAndKey(UUID organizationId, String key);
}
