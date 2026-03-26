package com.skillsync.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyReferralRequest {

    @NotBlank(message = "Referral code is required")
    @Size(max = 20, message = "Referral code must be at most 20 characters")
    private String referralCode;
}
