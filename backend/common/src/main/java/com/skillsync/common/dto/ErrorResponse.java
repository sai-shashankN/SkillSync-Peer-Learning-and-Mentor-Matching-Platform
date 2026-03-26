package com.skillsync.common.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String errorCode;
    private String error;
    private String message;
    private String path;
    private String correlationId;
    private Instant timestamp;
    private Map<String, String> validationErrors;
}
