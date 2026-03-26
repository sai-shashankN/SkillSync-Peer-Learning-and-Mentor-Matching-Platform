package com.skillsync.user.repository;

import com.skillsync.user.model.RewardBalance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardBalanceRepository extends JpaRepository<RewardBalance, Long> {

    Optional<RewardBalance> findByUserId(Long userId);
}
