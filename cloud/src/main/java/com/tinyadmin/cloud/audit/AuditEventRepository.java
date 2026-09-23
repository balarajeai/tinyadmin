package com.tinyadmin.cloud.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByOperationIdOrderByOccurredAtAsc(UUID operationId);
    List<AuditEvent> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId);
}
