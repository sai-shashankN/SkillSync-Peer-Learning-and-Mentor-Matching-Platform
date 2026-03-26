package com.skillsync.audit.repository;

import com.skillsync.audit.model.MentorPerformanceDaily;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorPerformanceDailyRepository extends JpaRepository<MentorPerformanceDaily, Long> {

    Optional<MentorPerformanceDaily> findByDateAndMentorId(LocalDate date, Long mentorId);
}
