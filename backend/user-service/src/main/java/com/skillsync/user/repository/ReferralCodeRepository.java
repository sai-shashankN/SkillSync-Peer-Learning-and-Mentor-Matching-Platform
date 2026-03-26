package com.skillsync.user.repository;

import com.skillsync.user.model.ReferralCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {

    Optional<ReferralCode> findByUserId(Long userId);

    Optional<ReferralCode> findByCode(String code);
}
