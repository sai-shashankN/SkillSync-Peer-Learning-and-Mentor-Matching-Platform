package com.skillsync.user.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.user.dto.AuthUserSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuthServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<AuthUserSummaryResponse>> AUTH_USER_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public AuthServiceClient(@Value("${services.auth.base-url:http://localhost:8081}") String authServiceBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceBaseUrl)
                .build();
    }

    public AuthUserSummaryResponse getUserSummary(Long userId) {
        try {
            ApiResponse<AuthUserSummaryResponse> response = restClient.get()
                    .uri("/auth/internal/users/{id}", userId)
                    .retrieve()
                    .body(AUTH_USER_RESPONSE);

            if (response == null || response.getData() == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }

            return response.getData();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            throw new IllegalStateException("Failed to fetch user summary from auth-service", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to fetch user summary from auth-service", ex);
        }
    }
}
