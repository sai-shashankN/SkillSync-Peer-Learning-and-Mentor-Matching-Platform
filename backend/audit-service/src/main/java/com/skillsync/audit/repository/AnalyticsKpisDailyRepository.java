package com.skillsync.audit.repository;

import com.skillsync.audit.model.AnalyticsKpisDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsKpisDailyRepository extends JpaRepository<AnalyticsKpisDaily, Long> {

    Optional<AnalyticsKpisDaily> findByDate(LocalDate date);

    List<AnalyticsKpisDaily> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    Optional<AnalyticsKpisDaily> findTopByOrderByDateDesc();
}
