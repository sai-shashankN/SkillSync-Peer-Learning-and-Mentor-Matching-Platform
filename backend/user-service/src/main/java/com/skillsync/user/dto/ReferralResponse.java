package com.skillsync.user.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralResponse {

    private Long referralId;
    private String referrerCode;
    private String status;
    private BigDecimal creditsAwarded;
    private String message;
}
