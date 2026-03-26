package com.skillsync.audit.controller;

import com.skillsync.audit.dto.AdminKpiResponse;
import com.skillsync.audit.dto.AuditLogResponse;
import com.skillsync.audit.dto.TimeSeriesDataPoint;
import com.skillsync.audit.dto.TopSkillResponse;
import com.skillsync.audit.service.AnalyticsService;
import com.skillsync.audit.service.AuditLogService;
import com.skillsync.audit.util.RequestHeaderUtils;
import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;
    private final AnalyticsService analyticsService;
    private final Clock clock = Clock.systemUTC();

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getLogs(
            HttpServletRequest request,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Audit logs fetched successfully",
                auditLogService.getLogs(userId, actionType, serviceName, from, to, pageable)
        ));
    }

    @GetMapping("/logs/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getUserLogs(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                "User audit logs fetched successfully",
                auditLogService.getUserLogs(userId, pageable)
        ));
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<ApiResponse<AdminKpiResponse>> getOverviewKpis(HttpServletRequest request) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Overview KPIs fetched successfully", analyticsService.getOverviewKpis()));
    }

    @GetMapping("/analytics/sessions")
    public ResponseEntity<ApiResponse<List<TimeSeriesDataPoint>>> getSessionTimeSeries(
            HttpServletRequest request,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Session analytics fetched successfully",
                analyticsService.getSessionTimeSeries(resolveFrom(from), resolveTo(to), groupBy)
        ));
    }

    @GetMapping("/analytics/revenue")
    public ResponseEntity<ApiResponse<List<TimeSeriesDataPoint>>> getRevenueTimeSeries(
            HttpServletRequest request,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Revenue analytics fetched successfully",
                analyticsService.getRevenueTimeSeries(resolveFrom(from), resolveTo(to), groupBy)
        ));
    }

    @GetMapping("/analytics/top-skills")
    public ResponseEntity<ApiResponse<List<TopSkillResponse>>> getTopSkills(
            HttpServletRequest request,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Top skills fetched successfully",
                analyticsService.getTopSkills(resolveFrom(from), resolveTo(to), limit)
        ));
    }

    private LocalDate resolveFrom(LocalDate from) {
        if (from != null) {
            return from;
        }
        return LocalDate.now(clock).minusDays(29);
    }

    private LocalDate resolveTo(LocalDate to) {
        if (to != null) {
            return to;
        }
        return LocalDate.now(clock);
    }
}
