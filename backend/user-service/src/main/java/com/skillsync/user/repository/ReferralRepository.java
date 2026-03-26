package com.skillsync.user.repository;

import com.skillsync.user.model.Referral;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByRefereeId(Long refereeId);

    boolean existsByRefereeId(Long refereeId);

    List<Referral> findAllByReferrerId(Long referrerId);
}
