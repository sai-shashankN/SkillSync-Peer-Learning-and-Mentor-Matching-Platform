package com.skillsync.payment.repository;

import com.skillsync.payment.model.MentorEarnings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorEarningsRepository extends JpaRepository<MentorEarnings, Long> {

    Optional<MentorEarnings> findByMentorId(Long mentorId);
}
