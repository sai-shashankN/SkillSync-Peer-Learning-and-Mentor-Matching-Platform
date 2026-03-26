package com.skillsync.payment.repository;

import com.skillsync.payment.model.Payout;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Page<Payout> findByMentorId(Long mentorId, Pageable pageable);

    Optional<Payout> findByIdempotencyKey(String idempotencyKey);
}
