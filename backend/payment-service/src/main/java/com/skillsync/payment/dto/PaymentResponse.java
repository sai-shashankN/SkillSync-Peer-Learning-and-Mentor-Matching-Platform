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
public class PaymentResponse {

    private Long id;
    private Long sessionId;
    private Long payerId;
    private Long payeeId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String provider;
    private String providerOrderId;
    private String providerPaymentId;
    private BigDecimal capturedAmount;
    private BigDecimal refundedAmount;
    private Instant capturedAt;
    private Instant createdAt;
}
