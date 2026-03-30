package com.skillsync.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.payment.config.PayPalConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class PayPalService {

    private static final String ORDER_ALREADY_CAPTURED = "ORDER_ALREADY_CAPTURED";

    private final RestTemplate restTemplate;
    private final PayPalConfig payPalConfig;
    private final ObjectMapper objectMapper;

    private volatile OAuthToken cachedToken;

    public PayPalOrderResult createOrder(BigDecimal amount, String currency, String customId) {
        JsonNode response = exchangeJson(
                "/v2/checkout/orders",
                HttpMethod.POST,
                authorizedJsonEntity(createOrderPayload(amount, currency, customId)),
                "Unable to create PayPal order at this time"
        );
        return new PayPalOrderResult(
                response.path("id").asText(null),
                response.path("status").asText("CREATED")
        );
    }

    public PayPalCaptureResult captureOrder(String orderId, String idempotencyKey) {
        try {
            JsonNode response = exchangeJson(
                    "/v2/checkout/orders/" + orderId + "/capture",
                    HttpMethod.POST,
                    authorizedJsonEntity(objectMapper.createObjectNode(), idempotencyKey),
                    "Unable to capture PayPal order at this time"
            );
            return extractCaptureResult(response);
        } catch (BadRequestException ex) {
            if (isAlreadyCaptured(ex)) {
                return getOrder(orderId);
            }
            throw ex;
        }
    }

    public PayPalCaptureResult getOrder(String orderId) {
        JsonNode response = exchangeJson(
                "/v2/checkout/orders/" + orderId,
                HttpMethod.GET,
                authorizedEntity(),
                "Unable to fetch PayPal order details at this time"
        );
        return extractCaptureResult(response);
    }

    public PayPalRefundResult refundCapture(String captureId, BigDecimal amount, String noteToPayer) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("amount", objectMapper.createObjectNode()
                .put("currency_code", payPalConfig.getCurrency())
                .put("value", formatAmount(amount)));
        if (StringUtils.hasText(noteToPayer)) {
            payload.put("note_to_payer", noteToPayer.trim());
        }

        JsonNode response = exchangeJson(
                "/v2/payments/captures/" + captureId + "/refund",
                HttpMethod.POST,
                authorizedJsonEntity(payload),
                "Unable to create PayPal refund at this time"
        );

        return new PayPalRefundResult(
                response.path("id").asText(null),
                response.path("status").asText("PENDING")
        );
    }

    public String getClientId() {
        return payPalConfig.getClientId();
    }

    public String getCurrency() {
        return payPalConfig.getCurrency();
    }

    private JsonNode createOrderPayload(BigDecimal amount, String currency, String customId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("intent", "CAPTURE");
        payload.set("purchase_units", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("custom_id", customId)
                        .set("amount", objectMapper.createObjectNode()
                                .put("currency_code", currency)
                                .put("value", formatAmount(amount)))
        ));
        payload.set("application_context", objectMapper.createObjectNode()
                .put("brand_name", payPalConfig.getBrandName())
                .put("shipping_preference", "NO_SHIPPING")
                .put("user_action", "PAY_NOW"));
        return payload;
    }

    private PayPalCaptureResult extractCaptureResult(JsonNode response) {
        String orderId = response.path("id").asText(null);
        String orderStatus = response.path("status").asText("");
        JsonNode purchaseUnits = response.path("purchase_units");
        for (JsonNode purchaseUnit : purchaseUnits) {
            JsonNode capture = purchaseUnit.path("payments").path("captures");
            if (capture.isArray() && !capture.isEmpty()) {
                JsonNode firstCapture = capture.get(0);
                return new PayPalCaptureResult(
                        orderId,
                        firstCapture.path("id").asText(null),
                        firstCapture.path("status").asText(orderStatus),
                        parseAmount(firstCapture.path("amount")),
                        firstCapture.path("amount").path("currency_code").asText(payPalConfig.getCurrency())
                );
            }
        }

        return new PayPalCaptureResult(orderId, null, orderStatus, null, payPalConfig.getCurrency());
    }

    private JsonNode exchangeJson(
            String path,
            HttpMethod method,
            HttpEntity<?> entity,
            String defaultMessage
    ) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    payPalConfig.getBaseUrl() + path,
                    method,
                    entity,
                    JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null) {
                throw new BadRequestException(defaultMessage);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            throw new BadRequestException(resolveErrorMessage(ex, defaultMessage));
        } catch (RestClientException ex) {
            throw new BadRequestException(defaultMessage);
        }
    }

    private HttpEntity<?> authorizedEntity() {
        return new HttpEntity<>(createAuthorizedHeaders(false, null));
    }

    private HttpEntity<?> authorizedJsonEntity(Object payload) {
        return new HttpEntity<>(payload, createAuthorizedHeaders(true, null));
    }

    private HttpEntity<?> authorizedJsonEntity(Object payload, String idempotencyKey) {
        return new HttpEntity<>(payload, createAuthorizedHeaders(true, idempotencyKey));
    }

    private HttpHeaders createAuthorizedHeaders(boolean includeJsonContentType, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (includeJsonContentType) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (StringUtils.hasText(idempotencyKey)) {
            headers.set("PayPal-Request-Id", idempotencyKey.trim());
        }
        return headers;
    }

    private String getAccessToken() {
        OAuthToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.value();
        }

        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return current.value();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(
                    (payPalConfig.getClientId() + ":" + payPalConfig.getClientSecret()).getBytes(StandardCharsets.UTF_8)
            ));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            JsonNode response = exchangeJson(
                    "/v1/oauth2/token",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    "Unable to authenticate with PayPal at this time"
            );

            String token = response.path("access_token").asText(null);
            int expiresIn = response.path("expires_in").asInt(300);
            if (!StringUtils.hasText(token)) {
                throw new BadRequestException("Unable to authenticate with PayPal at this time");
            }

            cachedToken = new OAuthToken(token, Instant.now().plusSeconds(expiresIn));
            return token;
        }
    }

    private boolean isAlreadyCaptured(BadRequestException ex) {
        return ex.getMessage() != null && ex.getMessage().contains(ORDER_ALREADY_CAPTURED);
    }

    private String resolveErrorMessage(HttpStatusCodeException ex, String defaultMessage) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode details = body.path("details");
            if (details.isArray() && !details.isEmpty()) {
                String issue = details.get(0).path("issue").asText(null);
                String description = details.get(0).path("description").asText(null);
                if (StringUtils.hasText(issue) && ORDER_ALREADY_CAPTURED.equals(issue)) {
                    return ORDER_ALREADY_CAPTURED;
                }
                if (StringUtils.hasText(description)) {
                    return description;
                }
            }

            String message = body.path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
        } catch (Exception ignored) {
            // Fall back to the default message.
        }

        return defaultMessage;
    }

    private BigDecimal parseAmount(JsonNode amountNode) {
        String value = amountNode.path("value").asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record OAuthToken(String value, Instant expiresAt) {
    }

    public record PayPalOrderResult(String orderId, String providerStatus) {
    }

    public record PayPalCaptureResult(
            String orderId,
            String captureId,
            String status,
            BigDecimal amount,
            String currency
    ) {
    }

    public record PayPalRefundResult(String refundId, String status) {
    }
}
