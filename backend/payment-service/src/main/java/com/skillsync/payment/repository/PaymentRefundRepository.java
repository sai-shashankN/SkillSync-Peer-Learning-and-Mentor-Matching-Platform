package com.skillsync.payment.repository;

import com.skillsync.payment.model.PaymentRefund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    List<PaymentRefund> findByPaymentId(Long paymentId);
}
