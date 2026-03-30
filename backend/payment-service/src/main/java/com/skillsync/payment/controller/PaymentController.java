package com.skillsync.payment.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.payment.dto.EarningsResponse;
import com.skillsync.payment.dto.InitiatePaymentRequest;
import com.skillsync.payment.dto.PaymentInitiateResponse;
import com.skillsync.payment.dto.PaymentResponse;
import com.skillsync.payment.dto.PaymentVerifyResponse;
import com.skillsync.payment.dto.PayoutRequest;
import com.skillsync.payment.dto.PayoutResponse;
import com.skillsync.payment.dto.RefundRequest;
import com.skillsync.payment.dto.VerifyPaymentRequest;
import com.skillsync.payment.model.enums.PaymentStatus;
import com.skillsync.payment.service.EarningsService;
import com.skillsync.payment.service.PaymentService;
import com.skillsync.payment.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final EarningsService earningsService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest initiatePaymentRequest
    ) {
        PaymentInitiateResponse response = paymentService.initiatePayment(
                RequestHeaderUtils.extractUserId(request),
                initiatePaymentRequest,
                requireIdempotencyKey(idempotencyKey)
        );
        return ResponseEntity.ok(ApiResponse.ok("Payment initiated successfully", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verifyPayment(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VerifyPaymentRequest verifyPaymentRequest
    ) {
        PaymentVerifyResponse response = paymentService.verifyPayment(
                RequestHeaderUtils.extractUserId(request),
                verifyPaymentRequest,
                requireIdempotencyKey(idempotencyKey)
        );
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully", response));
    }

    @PostMapping("/webhooks/paypal")
    public ResponseEntity<ApiResponse<Void>> processWebhook(
            @RequestBody String payload
    ) {
        paymentService.processWebhook(payload);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<PaymentResponse>> getMyPayments(
            HttpServletRequest request,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(paymentService.getMyPayments(RequestHeaderUtils.extractUserId(request), status, pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PagedResponse<PaymentResponse>> getUserPayments(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(paymentService.getUserPayments(userId, pageable));
    }

    @GetMapping("/mentor/me/earnings")
    public ResponseEntity<ApiResponse<EarningsResponse>> getMyEarnings(HttpServletRequest request) {
        RequestHeaderUtils.requireMentor(request);
        EarningsResponse response = earningsService.getEarnings(RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Earnings fetched successfully", response));
    }

    @PostMapping("/mentor/me/payout")
    public ResponseEntity<ApiResponse<PayoutResponse>> requestPayout(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PayoutRequest payoutRequest
    ) {
        RequestHeaderUtils.requireMentor(request);
        PayoutResponse response = earningsService.requestPayout(
                RequestHeaderUtils.extractUserId(request),
                payoutRequest,
                requireIdempotencyKey(idempotencyKey)
        );
        return ResponseEntity.ok(ApiResponse.ok("Payout requested successfully", response));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable Long id,
            @Valid @RequestBody RefundRequest refundRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        requireIdempotencyKey(idempotencyKey);
        PaymentResponse response = paymentService.refundPayment(id, RequestHeaderUtils.extractUserId(request), refundRequest);
        return ResponseEntity.ok(ApiResponse.ok("Payment refunded successfully", response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<PagedResponse<PaymentResponse>> getTransactions(
            HttpServletRequest request,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(paymentService.getAllTransactions(status, from, to, pageable));
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        return idempotencyKey.trim();
    }
}
