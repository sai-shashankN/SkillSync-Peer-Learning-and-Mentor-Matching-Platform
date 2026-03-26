package com.skillsync.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analytics_kpis_daily")
public class AnalyticsKpisDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Builder.Default
    @Column(name = "total_users", nullable = false)
    private Long totalUsers = 0L;

    @Builder.Default
    @Column(name = "new_users", nullable = false)
    private Long newUsers = 0L;

    @Builder.Default
    @Column(name = "total_sessions", nullable = false)
    private Long totalSessions = 0L;

    @Builder.Default
    @Column(name = "completed_sessions", nullable = false)
    private Long completedSessions = 0L;

    @Builder.Default
    @Column(name = "cancelled_sessions", nullable = false)
    private Long cancelledSessions = 0L;

    @Builder.Default
    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(name = "total_refunds", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRefunds = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(name = "active_mentors", nullable = false)
    private Long activeMentors = 0L;

    @Builder.Default
    @Column(name = "new_reviews", nullable = false)
    private Long newReviews = 0L;

    @Builder.Default
    @Column(name = "badges_awarded", nullable = false)
    private Long badgesAwarded = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
