package com.skillsync.payment.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsResponse {

    private Long mentorId;
    private BigDecimal totalEarned;
    private BigDecimal pendingBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private BigDecimal totalWithdrawn;
}
