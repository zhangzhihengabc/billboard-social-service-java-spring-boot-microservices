package com.billboard.social.gamegroup.repository;

import com.billboard.social.gamegroup.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    Page<AuditLog> findByGroupIdAndActionOrderByCreatedAtDesc(UUID groupId, String action, Pageable pageable);
}