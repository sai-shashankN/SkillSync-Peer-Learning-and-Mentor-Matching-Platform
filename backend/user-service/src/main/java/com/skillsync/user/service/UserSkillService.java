package com.skillsync.user.service;

import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.user.dto.AddUserSkillRequest;
import com.skillsync.user.dto.UserSkillResponse;
import com.skillsync.user.mapper.UserMapper;
import com.skillsync.user.model.Proficiency;
import com.skillsync.user.model.UserSkill;
import com.skillsync.user.repository.UserSkillRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSkillService {

    private final UserSkillRepository userSkillRepository;
    private final ProfileService profileService;
    private final UserMapper userMapper;

    public UserSkillService(
            UserSkillRepository userSkillRepository,
            ProfileService profileService,
            UserMapper userMapper
    ) {
        this.userSkillRepository = userSkillRepository;
        this.profileService = profileService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public List<UserSkillResponse> getUserSkills(Long userId) {
        profileService.getRequiredProfile(userId);
        return userSkillRepository.findAllByUserId(userId).stream()
                .map(userMapper::toSkillResponse)
                .toList();
    }

    @Transactional
    public UserSkillResponse addSkill(Long userId, AddUserSkillRequest request) {
        profileService.getRequiredProfile(userId);
        if (userSkillRepository.existsByUserIdAndSkillId(userId, request.getSkillId())) {
            throw new ConflictException("Skill already added for user");
        }

        UserSkill skill = UserSkill.builder()
                .userId(userId)
                .skillId(request.getSkillId())
                .proficiency(Proficiency.valueOf(request.getProficiency().trim().toUpperCase(Locale.ROOT)))
                .build();
        return userMapper.toSkillResponse(userSkillRepository.save(skill));
    }

    @Transactional
    public void removeSkill(Long userId, Long skillId) {
        profileService.getRequiredProfile(userId);
        UserSkill skill = userSkillRepository.findByUserIdAndSkillId(userId, skillId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSkill", "skillId", skillId));
        userSkillRepository.delete(skill);
    }
}
