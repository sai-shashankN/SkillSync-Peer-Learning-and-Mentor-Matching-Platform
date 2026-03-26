package com.skillsync.payment.event;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.SessionCompletedEvent;
import com.skillsync.payment.model.Payment;
import com.skillsync.payment.model.enums.PaymentStatus;
import com.skillsync.payment.repository.PaymentRepository;
import com.skillsync.payment.service.EarningsService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCompletedEventListener {

    private final PaymentRepository paymentRepository;
    private final EarningsService earningsService;

    @RabbitListener(queues = RabbitMQConstants.PAYMENT_SESSION_COMPLETED_QUEUE)
    public void handleSessionCompleted(SessionCompletedEvent event) {
        paymentRepository.findBySessionIdAndStatusIn(
                event.sessionId(),
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
        ).ifPresent(payment -> moveEarnings(event.mentorId(), payment));
    }

    private void moveEarnings(Long mentorId, Payment payment) {
        BigDecimal sessionAmount = payment.getCapturedAmount().subtract(payment.getRefundedAmount()).max(BigDecimal.ZERO.setScale(2));
        if (sessionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Skipping earnings release for sessionId={} because no balance remains", payment.getSessionId());
            return;
        }

        earningsService.movePendingToAvailable(mentorId, sessionAmount);
        log.info("Released earnings for mentorId={} sessionId={} amount={}", mentorId, payment.getSessionId(), sessionAmount);
    }
}
