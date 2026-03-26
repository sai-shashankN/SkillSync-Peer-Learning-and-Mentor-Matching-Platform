package com.skillsync.user.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.user.dto.ApplyReferralRequest;
import com.skillsync.user.dto.ReferralCodeResponse;
import com.skillsync.user.dto.ReferralResponse;
import com.skillsync.user.model.Referral;
import com.skillsync.user.model.ReferralCode;
import com.skillsync.user.model.ReferralStatus;
import com.skillsync.user.model.RewardBalance;
import com.skillsync.user.model.RewardTransaction;
import com.skillsync.user.model.RewardTransactionStatus;
import com.skillsync.user.model.RewardTransactionType;
import com.skillsync.user.repository.ReferralCodeRepository;
import com.skillsync.user.repository.ReferralRepository;
import com.skillsync.user.repository.RewardBalanceRepository;
import com.skillsync.user.repository.RewardTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralService {

    private static final BigDecimal REFERRER_CREDIT = new BigDecimal("50.00");
    private static final BigDecimal REFEREE_BONUS = new BigDecimal("25.00");

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final RewardBalanceRepository rewardBalanceRepository;
    private final RewardTransactionRepository rewardTransactionRepository;
    private final ProfileService profileService;
    private final ReferralCodeGenerator referralCodeGenerator;

    public ReferralService(
            ReferralCodeRepository referralCodeRepository,
            ReferralRepository referralRepository,
            RewardBalanceRepository rewardBalanceRepository,
            RewardTransactionRepository rewardTransactionRepository,
            ProfileService profileService,
            ReferralCodeGenerator referralCodeGenerator
    ) {
        this.referralCodeRepository = referralCodeRepository;
        this.referralRepository = referralRepository;
        this.rewardBalanceRepository = rewardBalanceRepository;
        this.rewardTransactionRepository = rewardTransactionRepository;
        this.profileService = profileService;
        this.referralCodeGenerator = referralCodeGenerator;
    }

    @Transactional(readOnly = true)
    public ReferralCodeResponse getReferralCode(Long userId) {
        profileService.getRequiredProfile(userId);
        ReferralCode referralCode = referralCodeGenerator.ensureReferralCode(userId);
        return ReferralCodeResponse.builder()
                .code(referralCode.getCode())
                .createdAt(referralCode.getCreatedAt())
                .build();
    }

    @Transactional
    public ReferralResponse applyReferralCode(Long userId, ApplyReferralRequest request) {
        profileService.getRequiredProfile(userId);
        String code = request.getReferralCode().trim().toUpperCase(Locale.ROOT);
        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("ReferralCode", "code", code));

        if (referralCode.getUserId().equals(userId)) {
            throw new BadRequestException("You cannot apply your own referral code");
        }
        if (referralRepository.existsByRefereeId(userId)) {
            throw new ConflictException("Referral code has already been applied for this user");
        }

        Referral referral = referralRepository.save(
                Referral.builder()
                        .referrerId(referralCode.getUserId())
                        .refereeId(userId)
                        .status(ReferralStatus.PENDING)
                        .creditsAwarded(BigDecimal.ZERO)
                        .build()
        );

        RewardBalance referrerBalance = getOrCreateRewardBalance(referralCode.getUserId());
        RewardBalance refereeBalance = getOrCreateRewardBalance(userId);

        referrerBalance.setReferralCreditBalance(referrerBalance.getReferralCreditBalance().add(REFERRER_CREDIT));
        refereeBalance.setReferralCreditBalance(refereeBalance.getReferralCreditBalance().add(REFEREE_BONUS));
        rewardBalanceRepository.save(referrerBalance);
        rewardBalanceRepository.save(refereeBalance);

        rewardTransactionRepository.save(
                RewardTransaction.builder()
                        .userId(referralCode.getUserId())
                        .referral(referral)
                        .type(RewardTransactionType.REFERRAL_CREDIT)
                        .amount(REFERRER_CREDIT)
                        .status(RewardTransactionStatus.COMPLETED)
                        .description("Referral credit for inviting user " + userId)
                        .build()
        );
        rewardTransactionRepository.save(
                RewardTransaction.builder()
                        .userId(userId)
                        .referral(referral)
                        .type(RewardTransactionType.REFERRAL_BONUS)
                        .amount(REFEREE_BONUS)
                        .status(RewardTransactionStatus.COMPLETED)
                        .description("Referral bonus for applying code " + code)
                        .build()
        );

        referral.setStatus(ReferralStatus.CREDITED);
        referral.setCreditedAt(Instant.now());
        referral.setCreditsAwarded(REFERRER_CREDIT.add(REFEREE_BONUS));
        Referral savedReferral = referralRepository.save(referral);

        return ReferralResponse.builder()
                .referralId(savedReferral.getId())
                .referrerCode(code)
                .status(savedReferral.getStatus().name())
                .creditsAwarded(savedReferral.getCreditsAwarded())
                .message("Referral applied successfully")
                .build();
    }

    private RewardBalance getOrCreateRewardBalance(Long userId) {
        return rewardBalanceRepository.findByUserId(userId)
                .orElseGet(() -> rewardBalanceRepository.save(
                        RewardBalance.builder()
                                .userId(userId)
                                .referralCreditBalance(BigDecimal.ZERO)
                                .build()
                ));
    }
}
