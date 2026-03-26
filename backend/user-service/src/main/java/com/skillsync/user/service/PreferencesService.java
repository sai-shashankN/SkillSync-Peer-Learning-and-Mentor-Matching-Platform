package com.skillsync.user.service;

import com.skillsync.user.dto.PreferencesResponse;
import com.skillsync.user.dto.UpdatePreferencesRequest;
import com.skillsync.user.mapper.UserMapper;
import com.skillsync.user.model.Profile;
import com.skillsync.user.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PreferencesService {

    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final UserMapper userMapper;

    public PreferencesService(ProfileRepository profileRepository, ProfileService profileService, UserMapper userMapper) {
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(Long userId) {
        Profile profile = profileService.getRequiredProfile(userId);
        return userMapper.toPreferencesResponse(profile);
    }

    @Transactional
    public PreferencesResponse updatePreferences(Long userId, UpdatePreferencesRequest request) {
        Profile profile = profileService.getRequiredProfile(userId);
        if (StringUtils.hasText(request.getTimezone())) {
            profile.setTimezone(request.getTimezone().trim());
        }
        if (StringUtils.hasText(request.getLanguage())) {
            profile.setLanguage(request.getLanguage().trim());
        }
        if (request.getDarkMode() != null) {
            profile.setDarkMode(request.getDarkMode());
        }
        return userMapper.toPreferencesResponse(profileRepository.save(profile));
    }
}
