package com.skillsync.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.payment.client.SessionClient;
import com.skillsync.payment.client.SessionClient.SessionSnapshot;
import com.skillsync.payment.config.RazorpayConfig;
import com.skillsync.payment.dto.InitiatePaymentRequest;
import com.skillsync.payment.dto.PaymentInitiateResponse;
import com.skillsync.payment.dto.PaymentResponse;
import com.skillsync.payment.dto.PaymentVerifyResponse;
import com.skillsync.payment.dto.RefundRequest;
import com.skillsync.payment.dto.VerifyPaymentRequest;
import com.skillsync.payment.mapper.PaymentMapper;
import com.skillsync.payment.model.Payment;
import com.skillsync.payment.model.PaymentRefund;
import com.skillsync.payment.model.PaymentWebhookEvent;
import com.skillsync.payment.model.enums.PaymentStatus;
import com.skillsync.payment.model.enums.RefundStatus;
import com.skillsync.payment.repository.PaymentRefundRepository;
import com.skillsync.payment.repository.PaymentRepository;
import com.skillsync.payment.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentMapper paymentMapper;
    private final RazorpayService razorpayService;
    private final RazorpayConfig razorpayConfig;
    private final SessionClient sessionClient;
    private final EventPublisherService eventPublisherService;
    private final EarningsService earningsService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentInitiateResponse initiatePayment(Long payerId, InitiatePaymentRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey.trim()).orElse(null);
        if (existing != null) {
            if (!existing.getPayerId().equals(payerId)) {
                throw new ConflictException("Idempotency key is already associated with another payer");
            }
            if (existing.getStatus() == PaymentStatus.INITIATED || existing.getStatus() == PaymentStatus.AUTHORIZED) {
                return buildInitiateResponse(existing);
            }
            throw new ConflictException("Payment has already been processed for this idempotency key");
        }

        SessionSnapshot session = sessionClient.getSession(request.getSessionId(), payerId);
        if (!session.learnerId().equals(payerId)) {
            throw new UnauthorizedException("You are not allowed to pay for this session");
        }
        if (!"PAYMENT_PENDING".equalsIgnoreCase(session.status())) {
            throw new BadRequestException("Session is not eligible for payment");
        }

        String receipt = buildReceipt(request.getSessionId(), request.getHoldId(), idempotencyKey.trim());
        RazorpayService.RazorpayOrderResult order = razorpayService.createOrder(session.amount(), "INR", receipt);

        Payment payment = Payment.builder()
                .sessionId(request.getSessionId())
                .payerId(payerId)
                .payeeId(session.mentorId())
                .amount(normalize(session.amount()))
                .currency("INR")
                .idempotencyKey(idempotencyKey.trim())
                .razorpayOrderId(order.razorpayOrderId())
                .providerReceipt(receipt)
                .providerStatus(order.providerStatus())
                .status(PaymentStatus.INITIATED)
                .build();

        return buildInitiateResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentVerifyResponse verifyPayment(Long payerId, VerifyPaymentRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        Payment existingCaptured = paymentRepository.findBySessionIdAndStatusIn(
                request.getSessionId(),
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
        ).orElse(null);
        if (existingCaptured != null) {
            if (!existingCaptured.getPayerId().equals(payerId)) {
                throw new UnauthorizedException("You are not allowed to verify this payment");
            }
            return buildVerifyResponse(existingCaptured);
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "razorpayOrderId", request.getRazorpayOrderId()));
        if (!payment.getPayerId().equals(payerId)) {
            throw new UnauthorizedException("You are not allowed to verify this payment");
        }
        if (!payment.getSessionId().equals(request.getSessionId())) {
            throw new BadRequestException("Payment does not belong to this session");
        }

        boolean validSignature = razorpayService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
        if (!validSignature) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderStatus("signature_failed");
            payment.setFailureMessage("Invalid Razorpay signature");
            paymentRepository.save(payment);
            sessionClient.markPaymentFailed(request.getSessionId());
            throw new BadRequestException("Invalid payment signature");
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setCapturedAmount(normalize(payment.getAmount()));
        payment.setCapturedAt(Instant.now());
        payment.setProviderStatus("captured");
        Payment savedPayment = paymentRepository.save(payment);

        sessionClient.markSessionPaid(request.getSessionId());
        earningsService.addPendingEarnings(savedPayment.getPayeeId(), savedPayment.getAmount());
        eventPublisherService.publishPaymentReceived(savedPayment);
        return buildVerifyResponse(savedPayment);
    }

    @Transactional
    public void processWebhook(String payload, String signature) {
        razorpayService.verifyWebhookSignature(payload, signature);

        PaymentWebhookEvent webhookEvent = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            String providerEventId = extractProviderEventId(root);
            if (webhookEventRepository.existsByProviderEventId(providerEventId)) {
                return;
            }

            webhookEvent = webhookEventRepository.save(PaymentWebhookEvent.builder()
                    .providerEventId(providerEventId)
                    .eventType(root.path("event").asText("unknown"))
                    .payloadJson(payload)
                    .processingStatus("PENDING")
                    .build());

            handleWebhookEvent(root);
            webhookEvent.setProcessingStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);
        } catch (Exception ex) {
            if (webhookEvent != null) {
                webhookEvent.setProcessingStatus("FAILED");
                webhookEvent.setProcessedAt(Instant.now());
                webhookEventRepository.save(webhookEvent);
            }
            if (ex instanceof BadRequestException badRequestException) {
                throw badRequestException;
            }
            throw new BadRequestException("Unable to process webhook payload");
        }
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, Long adminUserId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Payment cannot be refunded in its current state");
        }

        BigDecimal refundAmount = normalize(request.getAmount());
        BigDecimal totalRefunded = payment.getRefundedAmount().add(refundAmount);
        if (totalRefunded.compareTo(payment.getCapturedAmount()) > 0) {
            throw new BadRequestException("Refund amount exceeds captured amount");
        }

        PaymentRefund refund = PaymentRefund.builder()
                .paymentId(payment.getId())
                .sessionId(payment.getSessionId())
                .amount(refundAmount)
                .reason(request.getReason())
                .status(RefundStatus.INITIATED)
                .initiatedBy(adminUserId)
                .build();
        PaymentRefund savedRefund = paymentRefundRepository.save(refund);

        String providerRefundId = razorpayService.initiateRefund(payment.getRazorpayPaymentId(), refundAmount);
        savedRefund.setProviderRefundId(providerRefundId);
        savedRefund.setStatus(RefundStatus.COMPLETED);
        savedRefund.setProcessedAt(Instant.now());
        paymentRefundRepository.save(savedRefund);

        payment.setRefundedAmount(totalRefunded);
        payment.setRefundedAt(Instant.now());
        payment.setStatus(totalRefunded.compareTo(payment.getCapturedAmount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        Payment savedPayment = paymentRepository.save(payment);

        earningsService.reduceEarnings(savedPayment.getPayeeId(), refundAmount);
        eventPublisherService.publishPaymentRefunded(savedPayment, refundAmount);
        return paymentMapper.toPaymentResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getMyPayments(Long userId, PaymentStatus status, Pageable pageable) {
        return mapPage(paymentRepository.findByPayerIdAndOptionalStatus(userId, status, pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getUserPayments(Long userId, Pageable pageable) {
        return mapPage(paymentRepository.findByUserId(userId, pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getAllTransactions(
            PaymentStatus status,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return mapPage(paymentRepository.findAll(buildTransactionSpecification(status, from, to), pageable));
    }

    private void handleWebhookEvent(JsonNode root) {
        String eventType = root.path("event").asText("");
        switch (eventType) {
            case "payment.captured" -> handlePaymentCaptured(root);
            case "payment.failed" -> handlePaymentFailed(root);
            case "refund.processed" -> handleRefundProcessed(root);
            default -> {
                // No-op for unsupported event types in this phase.
            }
        }
    }

    private void handlePaymentCaptured(JsonNode root) {
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        if (!StringUtils.hasText(orderId)) {
            return;
        }

        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CAPTURED
                    || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                    || payment.getStatus() == PaymentStatus.REFUNDED) {
                return;
            }

            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setRazorpayPaymentId(paymentEntity.path("id").asText(null));
            payment.setCapturedAmount(normalize(fromPaise(paymentEntity.path("amount").asLong(0L))));
            payment.setCapturedAt(Instant.now());
            payment.setProviderStatus("captured");
            Payment savedPayment = paymentRepository.save(payment);

            sessionClient.markSessionPaid(savedPayment.getSessionId());
            earningsService.addPendingEarnings(savedPayment.getPayeeId(), savedPayment.getAmount());
            eventPublisherService.publishPaymentReceived(savedPayment);
        });
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        if (!StringUtils.hasText(orderId)) {
            return;
        }

        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderStatus(paymentEntity.path("status").asText("failed"));
            payment.setFailureCode(paymentEntity.path("error_code").asText(null));
            payment.setFailureMessage(paymentEntity.path("error_description").asText("Payment failed"));
            paymentRepository.save(payment);
            sessionClient.markPaymentFailed(payment.getSessionId());
        });
    }

    private void handleRefundProcessed(JsonNode root) {
        JsonNode refundEntity = root.path("payload").path("refund").path("entity");
        String paymentId = refundEntity.path("payment_id").asText(null);
        if (!StringUtils.hasText(paymentId)) {
            return;
        }

        paymentRepository.findByRazorpayPaymentId(paymentId).ifPresent(payment -> {
            BigDecimal refundAmount = normalize(fromPaise(refundEntity.path("amount").asLong(0L)));
            BigDecimal totalRefunded = payment.getRefundedAmount().add(refundAmount).min(payment.getCapturedAmount());
            payment.setRefundedAmount(totalRefunded);
            payment.setRefundedAt(Instant.now());
            payment.setStatus(totalRefunded.compareTo(payment.getCapturedAmount()) == 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED);
            payment.setProviderStatus("refund_processed");
            paymentRepository.save(payment);
        });
    }

    private PaymentInitiateResponse buildInitiateResponse(Payment payment) {
        return PaymentInitiateResponse.builder()
                .razorpayOrderId(payment.getRazorpayOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .razorpayKeyId(razorpayConfig.getKeyId())
                .build();
    }

    private PaymentVerifyResponse buildVerifyResponse(Payment payment) {
        return PaymentVerifyResponse.builder()
                .paymentId(payment.getId())
                .sessionId(payment.getSessionId())
                .status(payment.getStatus())
                .amount(payment.getCapturedAmount().compareTo(BigDecimal.ZERO) > 0 ? payment.getCapturedAmount() : payment.getAmount())
                .capturedAt(payment.getCapturedAt())
                .build();
    }

    private PagedResponse<PaymentResponse> mapPage(Page<Payment> page) {
        return PagedResponse.<PaymentResponse>builder()
                .content(page.getContent().stream().map(paymentMapper::toPaymentResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private Specification<Payment> buildTransactionSpecification(PaymentStatus status, Instant from, Instant to) {
        Specification<Payment> specification = Specification.where(null);

        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return specification;
    }

    private String buildReceipt(Long sessionId, Long holdId, String idempotencyKey) {
        String suffix = idempotencyKey.length() > 12 ? idempotencyKey.substring(0, 12) : idempotencyKey;
        return holdId == null
                ? "session-" + sessionId + "-" + suffix
                : "session-" + sessionId + "-hold-" + holdId + "-" + suffix;
    }

    private String extractProviderEventId(JsonNode root) {
        if (root.hasNonNull("id")) {
            return root.get("id").asText();
        }

        String eventType = root.path("event").asText("unknown");
        String primaryEntityId = root.path("payload").path("payment").path("entity").path("id").asText(null);
        if (!StringUtils.hasText(primaryEntityId)) {
            primaryEntityId = root.path("payload").path("refund").path("entity").path("id").asText("na");
        }
        return eventType + "-" + primaryEntityId + "-" + root.path("created_at").asText("0");
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
