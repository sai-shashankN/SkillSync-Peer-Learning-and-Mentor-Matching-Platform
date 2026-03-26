package com.skillsync.common.event;

import java.math.BigDecimal;

public record PaymentReceivedEvent(BaseEvent base, Long paymentId, Long sessionId, Long payerId, BigDecimal amount) {
}
