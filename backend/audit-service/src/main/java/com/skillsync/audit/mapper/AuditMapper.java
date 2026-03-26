package com.skillsync.audit.mapper;

import com.skillsync.audit.dto.AuditLogResponse;
import com.skillsync.audit.model.AuditLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditMapper {

    AuditLogResponse toAuditLogResponse(AuditLog auditLog);
}
