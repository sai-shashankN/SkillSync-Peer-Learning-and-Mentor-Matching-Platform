package com.skillsync.user.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardBalanceResponse {

    private BigDecimal balance;
    private List<RewardTransactionResponse> transactions;
}
