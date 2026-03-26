package com.skillsync.audit.service;

import com.skillsync.audit.model.AnalyticsKpisDaily;
import com.skillsync.audit.model.MentorPerformanceDaily;
import com.skillsync.audit.model.SkillPopularityDaily;
import com.skillsync.audit.repository.AnalyticsKpisDailyRepository;
import com.skillsync.audit.repository.MentorPerformanceDailyRepository;
import com.skillsync.audit.repository.SkillPopularityDailyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsProjectionService {

    private final AnalyticsKpisDailyRepository analyticsKpisDailyRepository;
    private final SkillPopularityDailyRepository skillPopularityDailyRepository;
    private final MentorPerformanceDailyRepository mentorPerformanceDailyRepository;

    @Transactional
    public void incrementNewUsers(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setNewUsers(kpi.getNewUsers() + 1);
        kpi.setTotalUsers(kpi.getTotalUsers() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementSessions(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setTotalSessions(kpi.getTotalSessions() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementCompletedSessions(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setCompletedSessions(kpi.getCompletedSessions() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementCancelledSessions(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setCancelledSessions(kpi.getCancelledSessions() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void addRevenue(LocalDate date, BigDecimal amount) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setTotalRevenue(normalize(kpi.getTotalRevenue().add(normalize(amount))));
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void addRefund(LocalDate date, BigDecimal amount) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setTotalRefunds(normalize(kpi.getTotalRefunds().add(normalize(amount))));
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementActiveMentors(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setActiveMentors(kpi.getActiveMentors() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementReviews(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setNewReviews(kpi.getNewReviews() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementBadges(LocalDate date) {
        AnalyticsKpisDaily kpi = getOrCreateKpi(date);
        kpi.setBadgesAwarded(kpi.getBadgesAwarded() + 1);
        analyticsKpisDailyRepository.save(kpi);
    }

    @Transactional
    public void incrementSkillPopularity(LocalDate date, Long skillId) {
        if (skillId == null) {
            return;
        }

        SkillPopularityDaily row = skillPopularityDailyRepository.findByDateAndSkillId(date, skillId)
                .orElseGet(() -> SkillPopularityDaily.builder().date(date).skillId(skillId).build());
        row.setSessionCount(row.getSessionCount() + 1);
        skillPopularityDailyRepository.save(row);
    }

    @Transactional
    public void updateMentorPerformance(LocalDate date, Long mentorId, String metric, BigDecimal value) {
        if (mentorId == null || metric == null) {
            return;
        }

        MentorPerformanceDaily performance = mentorPerformanceDailyRepository.findByDateAndMentorId(date, mentorId)
                .orElseGet(() -> MentorPerformanceDaily.builder().date(date).mentorId(mentorId).build());

        switch (metric) {
            case "completed" -> performance.setSessionsCompleted(performance.getSessionsCompleted() + 1);
            case "cancelled" -> performance.setSessionsCancelled(performance.getSessionsCancelled() + 1);
            case "revenue" -> performance.setRevenue(normalize(performance.getRevenue().add(normalize(value))));
            case "rating" -> {
                BigDecimal normalizedValue = normalize(value);
                if (performance.getAvgRating() == null) {
                    performance.setAvgRating(normalizedValue);
                } else {
                    performance.setAvgRating(normalize(performance.getAvgRating().add(normalizedValue)
                            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)));
                }
            }
            default -> {
                return;
            }
        }

        mentorPerformanceDailyRepository.save(performance);
    }

    private AnalyticsKpisDaily getOrCreateKpi(LocalDate date) {
        return analyticsKpisDailyRepository.findByDate(date)
                .orElseGet(() -> AnalyticsKpisDaily.builder().date(date).build());
    }

    private BigDecimal normalize(BigDecimal value) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        return source.setScale(2, RoundingMode.HALF_UP);
    }
}
