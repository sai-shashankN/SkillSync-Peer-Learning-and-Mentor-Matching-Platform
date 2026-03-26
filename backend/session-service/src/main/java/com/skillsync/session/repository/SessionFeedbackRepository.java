package com.skillsync.session.repository;

import com.skillsync.session.model.SessionFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {

    List<SessionFeedback> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
}
