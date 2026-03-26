package com.skillsync.user.repository;

import com.skillsync.user.model.RewardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    Page<RewardTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
