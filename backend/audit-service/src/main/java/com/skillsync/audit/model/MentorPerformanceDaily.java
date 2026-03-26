package com.skillsync.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "mentor_performance_daily")
public class MentorPerformanceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Builder.Default
    @Column(name = "sessions_completed", nullable = false)
    private Long sessionsCompleted = 0L;

    @Builder.Default
    @Column(name = "sessions_cancelled", nullable = false)
    private Long sessionsCancelled = 0L;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO.setScale(2);
}
