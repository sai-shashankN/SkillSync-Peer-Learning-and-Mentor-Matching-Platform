package com.skillsync.payment.service;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.BaseEvent;
import com.skillsync.common.event.PaymentReceivedEvent;
import com.skillsync.common.event.PaymentRefundedEvent;
import com.skillsync.common.util.EventFactory;
import com.skillsync.payment.model.Payment;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private static final String SERVICE_NAME = "payment-service";

    private final RabbitTemplate rabbitTemplate;

    public void publishPaymentReceived(Payment payment) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_PAYMENT_RECEIVED);
        PaymentReceivedEvent event = new PaymentReceivedEvent(
                baseEvent,
                payment.getId(),
                payment.getSessionId(),
                payment.getPayerId(),
                payment.getAmount()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_PAYMENT_RECEIVED, event);
    }

    public void publishPaymentRefunded(Payment payment, java.math.BigDecimal amount) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_PAYMENT_REFUNDED);
        PaymentRefundedEvent event = new PaymentRefundedEvent(baseEvent, payment.getId(), payment.getSessionId(), amount);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_PAYMENT_REFUNDED, event);
    }

    private BaseEvent buildBaseEvent(String eventType) {
        return EventFactory.create(eventType, SERVICE_NAME, MDC.get("correlationId"));
    }
}
