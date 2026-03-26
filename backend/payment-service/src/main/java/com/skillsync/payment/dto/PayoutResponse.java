package com.skillsync.payment.dto;

import com.skillsync.payment.model.enums.PayoutStatus;
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
public class PayoutResponse {

    private Long id;
    private Long mentorId;
    private BigDecimal amount;
    private PayoutStatus status;
    private Instant requestedAt;
    private Instant processedAt;
}
