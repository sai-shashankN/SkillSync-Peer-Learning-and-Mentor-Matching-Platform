package com.skillsync.mentor.repository;

import com.skillsync.mentor.model.Waitlist;
import com.skillsync.mentor.model.enums.WaitlistStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    List<Waitlist> findByMentorIdAndStatus(Long mentorId, WaitlistStatus status);

    Optional<Waitlist> findByMentorIdAndLearnerIdAndStatus(Long mentorId, Long learnerId, WaitlistStatus status);

    boolean existsByMentorIdAndLearnerId(Long mentorId, Long learnerId);
}
