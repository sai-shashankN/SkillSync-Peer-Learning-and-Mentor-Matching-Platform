package com.skillsync.user.dto;

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
public class RewardTransactionResponse {

    private Long id;
    private String type;
    private BigDecimal amount;
    private String status;
    private String description;
    private Instant createdAt;
}
