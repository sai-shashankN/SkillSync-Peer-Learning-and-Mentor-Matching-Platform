package com.skillsync.payment.repository;

import com.skillsync.payment.model.Payment;
import com.skillsync.payment.model.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findBySessionIdAndStatusIn(Long sessionId, List<PaymentStatus> statuses);

    Page<Payment> findByPayerId(Long payerId, Pageable pageable);

    Page<Payment> findByPayeeId(Long payeeId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.payerId = :payerId AND (:status IS NULL OR p.status = :status)")
    Page<Payment> findByPayerIdAndOptionalStatus(@Param("payerId") Long payerId, @Param("status") PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.payerId = :userId OR p.payeeId = :userId")
    Page<Payment> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
