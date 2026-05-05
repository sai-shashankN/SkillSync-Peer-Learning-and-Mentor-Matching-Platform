package com.skillsync.payment.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.security.InternalServiceAuth;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class SessionClient {

    private final RestTemplate restTemplate;
    private final String internalServiceToken;

    public SessionClient(
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Value("${internal.service-token:}") String internalServiceToken
    ) {
        this.restTemplate = restTemplate;
        this.internalServiceToken = internalServiceToken;
    }

    public SessionSnapshot getSession(Long sessionId, Long userId) {
        String url = "http://session-service/sessions/internal/" + sessionId;
        HttpHeaders headers = internalHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<ApiResponse<SessionPayload>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );
            ApiResponse<SessionPayload> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BadRequestException("Session details are unavailable");
            }
            SessionPayload payload = body.getData();
            return new SessionSnapshot(
                    payload.getId(),
                    payload.getMentorId(),
                    payload.getMentorUserId(),
                    payload.getLearnerId(),
                    payload.getAmount(),
                    payload.getStatus()
            );
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to fetch session details at this time");
        }
    }

    public void markSessionPaid(Long sessionId) {
        invokeInternalStatusUpdate(sessionId, "/mark-paid");
    }

    public void markPaymentFailed(Long sessionId) {
        invokeInternalStatusUpdate(sessionId, "/payment-failed");
    }

    private void invokeInternalStatusUpdate(Long sessionId, String pathSuffix) {
        String url = "http://session-service/sessions/internal/" + sessionId + pathSuffix;
        HttpHeaders headers = internalHeaders();
        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(headers), new ParameterizedTypeReference<ApiResponse<Object>>() {
            });
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to update session payment status at this time");
        }
    }

    public record SessionSnapshot(Long id, Long mentorId, Long mentorUserId, Long learnerId, BigDecimal amount, String status) {
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalServiceAuth.HEADER_NAME, internalServiceToken);
        return headers;
    }

    @Data
    private static class SessionPayload {
        private Long id;
        private Long mentorId;
        private Long mentorUserId;
        private Long learnerId;
        private BigDecimal amount;
        private String status;
    }
}
