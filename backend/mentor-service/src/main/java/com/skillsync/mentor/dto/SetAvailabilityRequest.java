package com.skillsync.mentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetAvailabilityRequest {

    @NotEmpty(message = "At least one availability slot is required")
    @Valid
    private List<AvailabilitySlotRequest> slots;
}
