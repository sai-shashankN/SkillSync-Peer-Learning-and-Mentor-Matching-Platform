package com.skillsync.mentor.repository;

import com.skillsync.mentor.model.MentorUnavailability;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorUnavailabilityRepository extends JpaRepository<MentorUnavailability, Long> {

    List<MentorUnavailability> findByMentorIdAndBlockedToAfter(Long mentorId, Instant now);
}
