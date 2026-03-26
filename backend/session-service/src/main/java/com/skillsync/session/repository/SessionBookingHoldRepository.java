package com.skillsync.session.repository;

import com.skillsync.session.model.SessionBookingHold;
import com.skillsync.session.model.enums.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionBookingHoldRepository extends JpaRepository<SessionBookingHold, Long> {

    Optional<SessionBookingHold> findByIdempotencyKey(String idempotencyKey);

    List<SessionBookingHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant now);
}
