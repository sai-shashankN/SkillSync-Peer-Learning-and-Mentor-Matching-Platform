package com.skillsync.user.mapper;

import com.skillsync.user.dto.PreferencesResponse;
import com.skillsync.user.dto.RewardTransactionResponse;
import com.skillsync.user.dto.UserProfileResponse;
import com.skillsync.user.dto.UserSkillResponse;
import com.skillsync.user.dto.UserSummaryResponse;
import com.skillsync.user.model.Profile;
import com.skillsync.user.model.RewardTransaction;
import com.skillsync.user.model.UserSkill;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "darkMode", source = "darkMode")
    PreferencesResponse toPreferencesResponse(Profile profile);

    @Mapping(target = "proficiency", expression = "java(skill.getProficiency().name())")
    UserSkillResponse toSkillResponse(UserSkill skill);

    @Mapping(target = "type", expression = "java(transaction.getType().name())")
    @Mapping(target = "status", expression = "java(transaction.getStatus().name())")
    RewardTransactionResponse toRewardTransactionResponse(RewardTransaction transaction);

    default UserProfileResponse toProfileResponse(
            Profile profile,
            String email,
            String name,
            List<String> roles,
            List<UserSkillResponse> skills
    ) {
        return UserProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .email(email)
                .name(name)
                .bio(profile.getBio())
                .phone(profile.getPhone())
                .avatarUrl(profile.getAvatarUrl())
                .timezone(profile.getTimezone())
                .language(profile.getLanguage())
                .darkMode(profile.isDarkMode())
                .marketingOptIn(profile.isMarketingOptIn())
                .profileVisibility(profile.getProfileVisibility().name())
                .roles(roles)
                .skills(skills)
                .createdAt(profile.getCreatedAt())
                .build();
    }

    default UserSummaryResponse toUserSummaryResponse(Profile profile) {
        return UserSummaryResponse.builder()
                .userId(profile.getUserId())
                .avatarUrl(profile.getAvatarUrl())
                .roles(List.of())
                .isActive(profile.getDeletedAt() == null)
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
