package com.skillsync.session.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.security.InternalServiceAuth;
import java.math.BigDecimal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class MentorClient {

    private final RestTemplate restTemplate;
    @Value("${internal.service-token:}")
    private String internalServiceToken;

    public MentorSnapshot getMentor(Long mentorId) {
        return fetchMentor("http://mentor-service/mentors/" + mentorId, null);
    }

    public MentorSnapshot getMentorByUserId(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalServiceAuth.HEADER_NAME, internalServiceToken);
        return fetchMentor("http://mentor-service/mentors/internal/by-user/" + userId, new HttpEntity<>(headers));
    }

    private MentorSnapshot fetchMentor(String url, HttpEntity<?> requestEntity) {
        try {
            ResponseEntity<ApiResponse<MentorPayload>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    }
            );

            ApiResponse<MentorPayload> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BadRequestException("Mentor details are unavailable");
            }

            MentorPayload mentor = body.getData();
            return new MentorSnapshot(mentor.getId(), mentor.getUserId(), mentor.getHourlyRate(), mentor.getStatus());
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to fetch mentor details at this time");
        }
    }

    public record MentorSnapshot(Long id, Long userId, BigDecimal hourlyRate, String status) {
    }

    @Data
    private static class MentorPayload {
        private Long id;
        private Long userId;
        private BigDecimal hourlyRate;
        private String status;
    }
}
