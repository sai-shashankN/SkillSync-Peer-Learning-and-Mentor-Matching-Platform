package com.skillsync.review.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.security.InternalServiceAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class SessionClient {

    private final RestTemplate restTemplate;
    @Value("${internal.service-token:}")
    private String internalServiceToken;

    public SessionSnapshot getSession(Long sessionId) {
        String url = "http://session-service/sessions/internal/" + sessionId;
        HttpHeaders headers = internalHeaders();

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
                throw new BadRequestException("Session service returned no session data");
            }

            SessionPayload payload = body.getData();
            return new SessionSnapshot(
                    payload.getId(),
                    payload.getMentorId(),
                    payload.getMentorUserId(),
                    payload.getLearnerId(),
                    payload.getSkillId(),
                    payload.getStatus()
            );
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Session", "id", sessionId);
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to fetch session details at this time");
        }
    }

    public long getCompletedSessionCount(Long learnerId, Long skillId) {
        String url = UriComponentsBuilder.fromUriString("http://session-service/sessions/internal/count")
                .queryParam("learnerId", learnerId)
                .queryParam("skillId", skillId)
                .queryParam("status", "COMPLETED")
                .toUriString();

        try {
            ResponseEntity<ApiResponse<Long>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            ApiResponse<Long> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BadRequestException("Session service returned no count data");
            }
            return body.getData();
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to fetch completed session count at this time");
        }
    }

    public record SessionSnapshot(Long sessionId, Long mentorId, Long mentorUserId, Long learnerId, Long skillId, String status) {
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalServiceAuth.HEADER_NAME, internalServiceToken);
        return headers;
    }

    private static class SessionPayload {
        private Long id;
        private Long mentorId;
        private Long mentorUserId;
        private Long learnerId;
        private Long skillId;
        private String status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getMentorId() {
            return mentorId;
        }

        public void setMentorId(Long mentorId) {
            this.mentorId = mentorId;
        }

        public Long getMentorUserId() {
            return mentorUserId;
        }

        public void setMentorUserId(Long mentorUserId) {
            this.mentorUserId = mentorUserId;
        }

        public Long getLearnerId() {
            return learnerId;
        }

        public void setLearnerId(Long learnerId) {
            this.learnerId = learnerId;
        }

        public Long getSkillId() {
            return skillId;
        }

        public void setSkillId(Long skillId) {
            this.skillId = skillId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
