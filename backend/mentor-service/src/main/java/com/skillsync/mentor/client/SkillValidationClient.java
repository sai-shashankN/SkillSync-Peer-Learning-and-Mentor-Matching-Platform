package com.skillsync.mentor.client;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class SkillValidationClient {

    private final RestTemplate restTemplate;

    public void validateSkillIds(Set<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return;
        }

        String idsParam = skillIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String url = "http://skill-service/skills/internal/validate?ids=" + idsParam;

        try {
            ResponseEntity<ApiResponse<List<SkillValidationResult>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            ApiResponse<List<SkillValidationResult>> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BadRequestException("Skill validation returned no data");
            }

            Set<Long> validSkillIds = body.getData().stream()
                    .map(SkillValidationResult::id)
                    .collect(Collectors.toSet());
            if (!validSkillIds.containsAll(skillIds)) {
                throw new BadRequestException("One or more skill IDs are invalid or inactive");
            }
        } catch (RestClientException ex) {
            throw new BadRequestException("Unable to validate skills at this time");
        }
    }

    private record SkillValidationResult(Long id) {
    }
}
