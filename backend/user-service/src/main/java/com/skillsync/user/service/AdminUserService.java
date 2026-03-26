package com.skillsync.user.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.user.dto.UserStatusResponse;
import com.skillsync.user.dto.UserSummaryResponse;
import com.skillsync.user.mapper.UserMapper;
import com.skillsync.user.model.Profile;
import com.skillsync.user.repository.ProfileRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {

    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final UserMapper userMapper;

    public AdminUserService(ProfileRepository profileRepository, ProfileService profileService, UserMapper userMapper) {
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryResponse> listUsers(String search, String role, String status, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        Page<Profile> profiles = profileRepository.searchProfiles(normalizedSearch, normalizedStatus, pageable);
        return PagedResponse.<UserSummaryResponse>builder()
                .content(profiles.stream().map(userMapper::toUserSummaryResponse).toList())
                .page(profiles.getNumber())
                .size(profiles.getSize())
                .totalElements(profiles.getTotalElements())
                .totalPages(profiles.getTotalPages())
                .last(profiles.isLast())
                .build();
    }

    @Transactional
    public UserStatusResponse banUser(Long userId) {
        Profile profile = profileService.getRequiredProfile(userId);
        profile.setDeletedAt(Instant.now());
        profileRepository.save(profile);
        return UserStatusResponse.builder()
                .userId(userId)
                .isActive(false)
                .message("User banned successfully")
                .build();
    }

    @Transactional
    public UserStatusResponse unbanUser(Long userId) {
        Profile profile = profileService.getRequiredProfile(userId);
        profile.setDeletedAt(null);
        profileRepository.save(profile);
        return UserStatusResponse.builder()
                .userId(userId)
                .isActive(true)
                .message("User unbanned successfully")
                .build();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"BANNED".equals(normalized)) {
            throw new BadRequestException("Status must be ACTIVE or BANNED");
        }
        return normalized;
    }
}
