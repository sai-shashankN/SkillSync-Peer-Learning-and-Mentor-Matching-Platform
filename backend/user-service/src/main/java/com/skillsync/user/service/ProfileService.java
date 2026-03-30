package com.skillsync.user.service;

import com.skillsync.user.client.AuthServiceClient;
import com.skillsync.user.dto.AuthUserSummaryResponse;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.user.dto.UpdateProfileRequest;
import com.skillsync.user.dto.UserProfileResponse;
import com.skillsync.user.dto.UserSkillResponse;
import com.skillsync.user.dto.UserSummaryResponse;
import com.skillsync.user.mapper.UserMapper;
import com.skillsync.user.model.Profile;
import com.skillsync.user.model.RewardBalance;
import com.skillsync.user.repository.ProfileRepository;
import com.skillsync.user.repository.RewardBalanceRepository;
import com.skillsync.user.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserSkillRepository userSkillRepository;
    private final RewardBalanceRepository rewardBalanceRepository;
    private final ReferralCodeGenerator referralCodeGenerator;
    private final UserMapper userMapper;
    private final AuthServiceClient authServiceClient;

    public ProfileService(
            ProfileRepository profileRepository,
            UserSkillRepository userSkillRepository,
            RewardBalanceRepository rewardBalanceRepository,
            ReferralCodeGenerator referralCodeGenerator,
            UserMapper userMapper,
            AuthServiceClient authServiceClient
    ) {
        this.profileRepository = profileRepository;
        this.userSkillRepository = userSkillRepository;
        this.rewardBalanceRepository = rewardBalanceRepository;
        this.referralCodeGenerator = referralCodeGenerator;
        this.userMapper = userMapper;
        this.authServiceClient = authServiceClient;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId, String email, String name, List<String> roles) {
        Profile profile = getRequiredProfile(userId);
        List<UserSkillResponse> skills = userSkillRepository.findAllByUserId(userId).stream()
                .map(userMapper::toSkillResponse)
                .toList();
        return userMapper.toProfileResponse(profile, email, name, roles, skills);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(Long userId) {
        Profile profile = getRequiredProfile(userId);
        List<UserSkillResponse> skills = userSkillRepository.findAllByUserId(userId).stream()
                .map(userMapper::toSkillResponse)
                .toList();
        return userMapper.toProfileResponse(profile, null, null, List.of(), skills);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getInternalUserSummary(Long userId) {
        Profile profile = getRequiredProfile(userId);
        AuthUserSummaryResponse authUser = authServiceClient.getUserSummary(userId);
        return userMapper.toUserSummaryResponse(profile, authUser);
    }

    @Transactional
    public Profile updateProfile(Long userId, UpdateProfileRequest request) {
        Profile profile = getRequiredProfile(userId);
        profile.setBio(request.getBio());
        profile.setPhone(request.getPhone());
        return profileRepository.save(profile);
    }

    @Transactional
    public void createProfileForNewUser(Long userId, String email, String name) {
        if (profileRepository.existsByUserId(userId)) {
            return;
        }

        profileRepository.save(Profile.builder().userId(userId).build());
        referralCodeGenerator.ensureReferralCode(userId);
        rewardBalanceRepository.save(
                RewardBalance.builder()
                        .userId(userId)
                        .referralCreditBalance(BigDecimal.ZERO)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public boolean profileExists(Long userId) {
        return profileRepository.existsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Profile getRequiredProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));
    }
}
