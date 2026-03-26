package com.skillsync.payment.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.payment.dto.EarningsResponse;
import com.skillsync.payment.dto.PayoutRequest;
import com.skillsync.payment.dto.PayoutResponse;
import com.skillsync.payment.mapper.PaymentMapper;
import com.skillsync.payment.model.MentorEarnings;
import com.skillsync.payment.model.Payout;
import com.skillsync.payment.model.enums.PayoutStatus;
import com.skillsync.payment.repository.MentorEarningsRepository;
import com.skillsync.payment.repository.PayoutRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EarningsService {

    private final MentorEarningsRepository mentorEarningsRepository;
    private final PayoutRepository payoutRepository;
    private final PaymentMapper paymentMapper;

    @Transactional
    public EarningsResponse getEarnings(Long mentorId) {
        return paymentMapper.toEarningsResponse(getOrCreateEarnings(mentorId));
    }

    @Transactional
    public void addPendingEarnings(Long mentorId, BigDecimal amount) {
        MentorEarnings earnings = getOrCreateEarnings(mentorId);
        earnings.setPendingBalance(earnings.getPendingBalance().add(normalize(amount)));
        mentorEarningsRepository.save(earnings);
    }

    @Transactional
    public void movePendingToAvailable(Long mentorId, BigDecimal amount) {
        MentorEarnings earnings = getOrCreateEarnings(mentorId);
        BigDecimal requestedAmount = normalize(amount);
        BigDecimal transferableAmount = earnings.getPendingBalance().min(requestedAmount);
        if (transferableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        earnings.setPendingBalance(earnings.getPendingBalance().subtract(transferableAmount));
        earnings.setAvailableBalance(earnings.getAvailableBalance().add(transferableAmount));
        earnings.setTotalEarned(earnings.getTotalEarned().add(transferableAmount));
        mentorEarningsRepository.save(earnings);
    }

    @Transactional
    public PayoutResponse requestPayout(Long mentorId, PayoutRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        Payout existingPayout = payoutRepository.findByIdempotencyKey(idempotencyKey.trim()).orElse(null);
        if (existingPayout != null) {
            if (!existingPayout.getMentorId().equals(mentorId)) {
                throw new ConflictException("Idempotency key is already associated with another mentor");
            }
            return paymentMapper.toPayoutResponse(existingPayout);
        }

        return createPayout(mentorId, request, idempotencyKey.trim());
    }

    @Transactional
    public void reduceEarnings(Long mentorId, BigDecimal amount) {
        MentorEarnings earnings = getOrCreateEarnings(mentorId);
        BigDecimal remaining = normalize(amount);

        BigDecimal availableReduction = earnings.getAvailableBalance().min(remaining);
        earnings.setAvailableBalance(earnings.getAvailableBalance().subtract(availableReduction));
        earnings.setTotalEarned(earnings.getTotalEarned().subtract(availableReduction).max(BigDecimal.ZERO.setScale(2)));
        remaining = remaining.subtract(availableReduction);

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pendingReduction = earnings.getPendingBalance().min(remaining);
            earnings.setPendingBalance(earnings.getPendingBalance().subtract(pendingReduction));
        }

        mentorEarningsRepository.save(earnings);
    }

    private PayoutResponse createPayout(Long mentorId, PayoutRequest request, String idempotencyKey) {
        MentorEarnings earnings = getOrCreateEarnings(mentorId);
        BigDecimal amount = normalize(request.getAmount());
        if (earnings.getAvailableBalance().compareTo(amount) < 0) {
            throw new ConflictException("Insufficient available balance for payout");
        }

        earnings.setAvailableBalance(earnings.getAvailableBalance().subtract(amount));
        earnings.setLockedBalance(earnings.getLockedBalance().add(amount));
        mentorEarningsRepository.save(earnings);

        Payout payout = Payout.builder()
                .mentorId(mentorId)
                .amount(amount)
                .status(PayoutStatus.REQUESTED)
                .idempotencyKey(idempotencyKey)
                .build();
        return paymentMapper.toPayoutResponse(payoutRepository.save(payout));
    }

    private MentorEarnings getOrCreateEarnings(Long mentorId) {
        return mentorEarningsRepository.findByMentorId(mentorId)
                .orElseGet(() -> mentorEarningsRepository.save(MentorEarnings.builder().mentorId(mentorId).build()));
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
