package com.skillsync.audit.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String actionType;
    private String serviceName;
    private String resourceType;
    private String resourceId;
    private String details;
    private String correlationId;
    private String actorType;
    private String outcome;
    private Instant createdAt;
}
