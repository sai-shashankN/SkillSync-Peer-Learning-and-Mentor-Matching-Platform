package com.skillsync.audit.service;

import com.skillsync.audit.dto.AdminKpiResponse;
import com.skillsync.audit.dto.TimeSeriesDataPoint;
import com.skillsync.audit.dto.TopSkillResponse;
import com.skillsync.audit.model.AnalyticsKpisDaily;
import com.skillsync.audit.repository.AnalyticsKpisDailyRepository;
import com.skillsync.audit.repository.SkillPopularityDailyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsKpisDailyRepository analyticsKpisDailyRepository;
    private final SkillPopularityDailyRepository skillPopularityDailyRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public AdminKpiResponse getOverviewKpis() {
        LocalDate today = LocalDate.now(clock);
        AnalyticsKpisDaily kpi = analyticsKpisDailyRepository.findByDate(today)
                .or(() -> analyticsKpisDailyRepository.findTopByOrderByDateDesc())
                .orElseGet(() -> AnalyticsKpisDaily.builder().date(today).build());

        return AdminKpiResponse.builder()
                .totalUsers(kpi.getTotalUsers())
                .newUsersToday(kpi.getNewUsers())
                .totalSessions(kpi.getTotalSessions())
                .completedSessions(kpi.getCompletedSessions())
                .cancelledSessions(kpi.getCancelledSessions())
                .totalRevenue(kpi.getTotalRevenue())
                .totalRefunds(kpi.getTotalRefunds())
                .activeMentors(kpi.getActiveMentors())
                .newReviews(kpi.getNewReviews())
                .badgesAwarded(kpi.getBadgesAwarded())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesDataPoint> getSessionTimeSeries(LocalDate from, LocalDate to, String groupBy) {
        List<AnalyticsKpisDaily> rows = analyticsKpisDailyRepository.findByDateBetweenOrderByDateAsc(from, to);
        return buildSeries(rows, groupBy, row -> BigDecimal.valueOf(row.getTotalSessions()));
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesDataPoint> getRevenueTimeSeries(LocalDate from, LocalDate to, String groupBy) {
        List<AnalyticsKpisDaily> rows = analyticsKpisDailyRepository.findByDateBetweenOrderByDateAsc(from, to);
        return buildSeries(rows, groupBy, AnalyticsKpisDaily::getTotalRevenue);
    }

    @Transactional(readOnly = true)
    public List<TopSkillResponse> getTopSkills(LocalDate from, LocalDate to, int limit) {
        return skillPopularityDailyRepository.findTopSkills(from, to, PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(row -> TopSkillResponse.builder()
                        .skillId(row.getSkillId())
                        .sessionCount(row.getSessionCount())
                        .build())
                .toList();
    }

    private List<TimeSeriesDataPoint> buildSeries(
            List<AnalyticsKpisDaily> rows,
            String groupBy,
            MetricExtractor extractor
    ) {
        String normalizedGroupBy = groupBy == null ? "day" : groupBy.trim().toLowerCase();
        Map<String, BigDecimal> points = new LinkedHashMap<>();

        for (AnalyticsKpisDaily row : rows) {
            String key = switch (normalizedGroupBy) {
                case "week" -> row.getDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toString();
                default -> row.getDate().toString();
            };
            points.merge(key, extractor.extract(row), BigDecimal::add);
        }

        return points.entrySet().stream()
                .map(entry -> TimeSeriesDataPoint.builder()
                        .date(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    @FunctionalInterface
    private interface MetricExtractor {
        BigDecimal extract(AnalyticsKpisDaily row);
    }
}
