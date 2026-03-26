package com.skillsync.audit.repository;

import com.skillsync.audit.model.SkillPopularityDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillPopularityDailyRepository extends JpaRepository<SkillPopularityDaily, Long> {

    interface TopSkillAggregate {
        Long getSkillId();

        Long getSessionCount();
    }

    Optional<SkillPopularityDaily> findByDateAndSkillId(LocalDate date, Long skillId);

    @Query("SELECT s.skillId AS skillId, SUM(s.sessionCount) AS sessionCount "
            + "FROM SkillPopularityDaily s "
            + "WHERE s.date BETWEEN :from AND :to "
            + "GROUP BY s.skillId "
            + "ORDER BY SUM(s.sessionCount) DESC")
    List<TopSkillAggregate> findTopSkills(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);
}
