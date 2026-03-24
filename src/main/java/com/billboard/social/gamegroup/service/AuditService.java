package com.billboard.social.gamegroup.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.gamegroup.dto.response.GameGroupResponses.AuditLogResponse;
import com.billboard.social.gamegroup.entity.AuditLog;
import com.billboard.social.gamegroup.repository.AuditLogRepository;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final GroupMemberRepository memberRepository;

    /**
     * Asynchronously log an admin action. Fire-and-forget: errors are logged but not propagated.
     */
    @Async
    public void log(UUID groupId, Long actorUserId, String action, String targetType, String targetId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .groupId(groupId)
                    .actorUserId(actorUserId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit log written: group={} actor={} action={}", groupId, actorUserId, action);
        } catch (Exception e) {
            log.error("Failed to write audit log: group={} action={} error={}", groupId, action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLog(Long requestingUserId, UUID groupId, int page, int size) {
        // Only admins and owners can view audit logs
        memberRepository.findByGroupIdAndUserId(groupId, requestingUserId)
                .filter(m -> m.getRole() == MemberRole.ADMIN || m.getRole() == MemberRole.OWNER)
                .orElseThrow(() -> new ForbiddenException("Admin access required to view audit log"));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageRequest);
        return PageResponse.from(logs, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogByAction(Long requestingUserId, UUID groupId, String action, int page, int size) {
        memberRepository.findByGroupIdAndUserId(groupId, requestingUserId)
                .filter(m -> m.getRole() == MemberRole.ADMIN || m.getRole() == MemberRole.OWNER)
                .orElseThrow(() -> new ForbiddenException("Admin access required to view audit log"));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogRepository.findByGroupIdAndActionOrderByCreatedAtDesc(groupId, action, pageRequest);
        return PageResponse.from(logs, this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .groupId(log.getGroupId())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}