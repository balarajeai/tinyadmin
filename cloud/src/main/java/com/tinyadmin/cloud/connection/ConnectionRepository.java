package com.tinyadmin.cloud.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, UUID> {
    Optional<Connection> findByIdAndOrganizationIdAndEnvironmentId(
        UUID id, UUID organizationId, UUID environmentId
    );
}
