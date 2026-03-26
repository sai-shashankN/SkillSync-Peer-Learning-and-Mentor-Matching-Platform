package com.skillsync.payment.dto;

import com.skillsync.payment.model.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyResponse {

    private Long paymentId;
    private Long sessionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private Instant capturedAt;
}
