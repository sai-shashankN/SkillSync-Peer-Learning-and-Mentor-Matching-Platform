package com.skillsync.audit.service;

import com.skillsync.audit.dto.AuditLogResponse;
import com.skillsync.audit.mapper.AuditMapper;
import com.skillsync.audit.model.AuditLog;
import com.skillsync.audit.repository.AuditLogRepository;
import com.skillsync.common.dto.PagedResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditMapper auditMapper;

    @Transactional
    public void logEvent(
            Long userId,
            String actionType,
            String serviceName,
            String resourceType,
            String resourceId,
            String detailsJson,
            String correlationId,
            String actorType
    ) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .actionType(actionType)
                .serviceName(serviceName)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(detailsJson)
                .correlationId(correlationId)
                .actorType(actorType)
                .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getLogs(
            Long userId,
            String actionType,
            String serviceName,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return mapPage(auditLogRepository.findAll(
                buildLogSpecification(userId, actionType, serviceName, from, to),
                pageable
        ));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getUserLogs(Long userId, Pageable pageable) {
        return mapPage(auditLogRepository.findByUserId(userId, pageable));
    }

    private PagedResponse<AuditLogResponse> mapPage(Page<AuditLog> page) {
        return PagedResponse.<AuditLogResponse>builder()
                .content(page.getContent().stream().map(auditMapper::toAuditLogResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private Specification<AuditLog> buildLogSpecification(
            Long userId,
            String actionType,
            String serviceName,
            Instant from,
            Instant to
    ) {
        Specification<AuditLog> specification = Specification.where(null);

        if (userId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("userId"), userId));
        }
        if (actionType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("actionType"), actionType));
        }
        if (serviceName != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("serviceName"), serviceName));
        }
        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return specification;
    }
}
