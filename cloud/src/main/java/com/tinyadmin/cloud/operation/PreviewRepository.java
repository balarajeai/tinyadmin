package com.tinyadmin.cloud.operation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PreviewRepository extends JpaRepository<Preview, UUID> {
}
