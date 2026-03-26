package com.skillsync.common.event;

import java.math.BigDecimal;

public record PaymentRefundedEvent(BaseEvent base, Long paymentId, Long sessionId, BigDecimal amount) {
}
