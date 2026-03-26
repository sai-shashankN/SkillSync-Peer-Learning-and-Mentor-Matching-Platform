package com.skillsync.payment.mapper;

import com.skillsync.payment.dto.EarningsResponse;
import com.skillsync.payment.dto.PaymentResponse;
import com.skillsync.payment.dto.PayoutResponse;
import com.skillsync.payment.dto.RefundResponse;
import com.skillsync.payment.model.MentorEarnings;
import com.skillsync.payment.model.Payment;
import com.skillsync.payment.model.PaymentRefund;
import com.skillsync.payment.model.Payout;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toPaymentResponse(Payment payment);

    RefundResponse toRefundResponse(PaymentRefund refund);

    EarningsResponse toEarningsResponse(MentorEarnings earnings);

    PayoutResponse toPayoutResponse(Payout payout);
}
