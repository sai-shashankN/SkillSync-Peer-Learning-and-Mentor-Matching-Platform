package com.skillsync.review.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
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

    public SessionSnapshot getSession(Long sessionId) {
        String url = "http://session-service/sessions/internal/" + sessionId;

        try {
            ResponseEntity<ApiResponse<SessionPayload>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
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
                    null,
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

    public record SessionSnapshot(Long sessionId, Long mentorId, Long learnerId, Long skillId, String status) {
    }

    private static class SessionPayload {
        private Long id;
        private Long mentorId;
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
