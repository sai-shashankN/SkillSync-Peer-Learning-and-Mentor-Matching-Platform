package com.skillsync.mentor.mapper;

import com.skillsync.mentor.dto.AvailabilityResponse;
import com.skillsync.mentor.dto.MentorDetailResponse;
import com.skillsync.mentor.dto.MentorResponse;
import com.skillsync.mentor.dto.MentorSummaryResponse;
import com.skillsync.mentor.dto.UnavailabilityResponse;
import com.skillsync.mentor.dto.WaitlistResponse;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.MentorAvailability;
import com.skillsync.mentor.model.MentorUnavailability;
import com.skillsync.mentor.model.Waitlist;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MentorMapper {

    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "status", expression = "java(mentor.getStatus().name())")
    MentorResponse toMentorResponse(Mentor mentor);

    @Mapping(target = "userName", ignore = true)
    MentorSummaryResponse toMentorSummaryResponse(Mentor mentor);

    AvailabilityResponse toAvailabilityResponse(MentorAvailability availability);

    UnavailabilityResponse toUnavailabilityResponse(MentorUnavailability unavailability);

    @Mapping(target = "status", expression = "java(waitlist.getStatus().name())")
    WaitlistResponse toWaitlistResponse(Waitlist waitlist);

    default MentorResponse toMentorResponse(Mentor mentor, String userName) {
        MentorResponse response = toMentorResponse(mentor);
        response.setUserName(userName);
        return response;
    }

    default MentorSummaryResponse toMentorSummaryResponse(Mentor mentor, String userName) {
        MentorSummaryResponse response = toMentorSummaryResponse(mentor);
        response.setUserName(userName);
        return response;
    }

    default MentorDetailResponse toMentorDetailResponse(
            Mentor mentor,
            String userName,
            List<AvailabilityResponse> availability
    ) {
        return MentorDetailResponse.builder()
                .id(mentor.getId())
                .userId(mentor.getUserId())
                .userName(userName)
                .headline(mentor.getHeadline())
                .bio(mentor.getBio())
                .experienceYears(mentor.getExperienceYears())
                .hourlyRate(mentor.getHourlyRate())
                .avgRating(mentor.getAvgRating())
                .totalSessions(mentor.getTotalSessions())
                .totalReviews(mentor.getTotalReviews())
                .status(mentor.getStatus().name())
                .skillIds(mentor.getSkillIds())
                .availability(availability)
                .createdAt(mentor.getCreatedAt())
                .build();
    }
}
