package com.skillsync.mentor.repository;

import com.skillsync.mentor.model.MentorAvailability;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorAvailabilityRepository extends JpaRepository<MentorAvailability, Long> {

    List<MentorAvailability> findByMentorIdAndIsActiveTrue(Long mentorId);

    void deleteByMentorId(Long mentorId);
}
