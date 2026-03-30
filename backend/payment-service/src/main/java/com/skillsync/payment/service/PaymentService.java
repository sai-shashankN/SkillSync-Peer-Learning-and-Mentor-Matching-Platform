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
import com.skillsync.payment.config.PayPalConfig;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PAYPAL_PROVIDER = "PAYPAL";

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentMapper paymentMapper;
    private final PayPalService payPalService;
    private final PayPalConfig payPalConfig;
    private final SessionClient sessionClient;
    private final EventPublisherService eventPublisherService;
    private final EarningsService earningsService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentInitiateResponse initiatePayment(Long payerId, InitiatePaymentRequest request, String idempotencyKey) {
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        Payment existing = paymentRepository.findByIdempotencyKey(normalizedIdempotencyKey).orElse(null);
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

        String receipt = buildReceipt(request.getSessionId(), request.getHoldId(), normalizedIdempotencyKey);
        PayPalService.PayPalOrderResult order = payPalService.createOrder(
                session.amount(),
                payPalConfig.getCurrency(),
                receipt
        );

        Payment payment = Payment.builder()
                .sessionId(request.getSessionId())
                .payerId(payerId)
                .payeeId(session.mentorId())
                .amount(normalize(session.amount()))
                .currency(payPalConfig.getCurrency())
                .idempotencyKey(normalizedIdempotencyKey)
                .provider(PAYPAL_PROVIDER)
                .providerOrderId(order.orderId())
                .providerReceipt(receipt)
                .providerStatus(order.providerStatus())
                .status(PaymentStatus.INITIATED)
                .build();

        return buildInitiateResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentVerifyResponse verifyPayment(Long payerId, VerifyPaymentRequest request, String idempotencyKey) {
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

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

        Payment payment = paymentRepository.findByProviderOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", request.getOrderId()));
        if (!payment.getPayerId().equals(payerId)) {
            throw new UnauthorizedException("You are not allowed to verify this payment");
        }
        if (!payment.getSessionId().equals(request.getSessionId())) {
            throw new BadRequestException("Payment does not belong to this session");
        }

        PayPalService.PayPalCaptureResult capture = payPalService.captureOrder(request.getOrderId(), normalizedIdempotencyKey);
        if (!StringUtils.hasText(capture.captureId()) || !"COMPLETED".equalsIgnoreCase(capture.status())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderStatus(capture.status());
            payment.setFailureMessage("PayPal order capture did not complete successfully");
            paymentRepository.save(payment);
            sessionClient.markPaymentFailed(request.getSessionId());
            throw new BadRequestException("Unable to capture PayPal payment");
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderPaymentId(capture.captureId());
        payment.setProviderSignature(request.getOrderId());
        payment.setCapturedAmount(normalize(capture.amount() != null ? capture.amount() : payment.getAmount()));
        payment.setCapturedAt(Instant.now());
        payment.setProviderStatus(capture.status());
        Payment savedPayment = paymentRepository.save(payment);

        sessionClient.markSessionPaid(request.getSessionId());
        earningsService.addPendingEarnings(savedPayment.getPayeeId(), savedPayment.getAmount());
        eventPublisherService.publishPaymentReceived(savedPayment);
        return buildVerifyResponse(savedPayment);
    }

    @Transactional
    public void processWebhook(String payload) {
        PaymentWebhookEvent webhookEvent = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            String providerEventId = extractProviderEventId(root);
            if (webhookEventRepository.existsByProviderEventId(providerEventId)) {
                return;
            }

            webhookEvent = webhookEventRepository.save(PaymentWebhookEvent.builder()
                    .providerEventId(providerEventId)
                    .eventType(root.path("event_type").asText("unknown"))
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

        BigDecimal remainingRefundable = payment.getCapturedAmount().subtract(payment.getRefundedAmount()).max(BigDecimal.ZERO.setScale(2));
        BigDecimal refundAmount = request.getAmount() == null ? remainingRefundable : normalize(request.getAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("No refundable balance remains for this payment");
        }

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

        PayPalService.PayPalRefundResult refundResult = payPalService.refundCapture(
                payment.getProviderPaymentId(),
                refundAmount,
                request.getReason()
        );
        savedRefund.setProviderRefundId(refundResult.refundId());
        savedRefund.setStatus("COMPLETED".equalsIgnoreCase(refundResult.status())
                ? RefundStatus.COMPLETED
                : RefundStatus.PROCESSING);
        savedRefund.setProcessedAt(Instant.now());
        paymentRefundRepository.save(savedRefund);

        payment.setRefundedAmount(totalRefunded);
        payment.setRefundedAt(Instant.now());
        payment.setProviderStatus(refundResult.status());
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
        String eventType = root.path("event_type").asText("");
        switch (eventType) {
            case "PAYMENT.CAPTURE.COMPLETED" -> handlePaymentCaptured(root);
            case "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.DECLINED" -> handlePaymentFailed(root);
            default -> {
                // Webhooks are optional for the local PayPal Sandbox demo flow.
            }
        }
    }

    private void handlePaymentCaptured(JsonNode root) {
        JsonNode resource = root.path("resource");
        String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText(null);
        if (!StringUtils.hasText(orderId)) {
            return;
        }

        paymentRepository.findByProviderOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CAPTURED
                    || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                    || payment.getStatus() == PaymentStatus.REFUNDED) {
                return;
            }

            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setProviderPaymentId(resource.path("id").asText(null));
            payment.setCapturedAmount(normalize(parseWebhookAmount(resource.path("amount"))));
            payment.setCapturedAt(Instant.now());
            payment.setProviderStatus(resource.path("status").asText("COMPLETED"));
            Payment savedPayment = paymentRepository.save(payment);

            sessionClient.markSessionPaid(savedPayment.getSessionId());
            earningsService.addPendingEarnings(savedPayment.getPayeeId(), savedPayment.getAmount());
            eventPublisherService.publishPaymentReceived(savedPayment);
        });
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode resource = root.path("resource");
        String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText(null);
        if (!StringUtils.hasText(orderId)) {
            return;
        }

        paymentRepository.findByProviderOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderStatus(resource.path("status").asText("FAILED"));
            payment.setFailureCode(root.path("event_type").asText(null));
            payment.setFailureMessage("PayPal capture failed");
            paymentRepository.save(payment);
            sessionClient.markPaymentFailed(payment.getSessionId());
        });
    }

    private PaymentInitiateResponse buildInitiateResponse(Payment payment) {
        return PaymentInitiateResponse.builder()
                .orderId(payment.getProviderOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .clientId(payPalService.getClientId())
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

        String eventType = root.path("event_type").asText("unknown");
        String primaryEntityId = root.path("resource").path("id").asText(null);
        if (!StringUtils.hasText(primaryEntityId)) {
            primaryEntityId = root.path("resource")
                    .path("supplementary_data")
                    .path("related_ids")
                    .path("order_id")
                    .asText("na");
        }
        return eventType + "-" + primaryEntityId + "-" + root.path("create_time").asText("0");
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        return idempotencyKey.trim();
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseWebhookAmount(JsonNode amountNode) {
        String value = amountNode.path("value").asText(null);
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
