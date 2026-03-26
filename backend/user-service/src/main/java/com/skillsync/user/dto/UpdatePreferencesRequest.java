package com.skillsync.user.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {

    @Size(max = 50, message = "Timezone must be at most 50 characters")
    private String timezone;

    @Size(max = 10, message = "Language must be at most 10 characters")
    private String language;

    private Boolean darkMode;
}
