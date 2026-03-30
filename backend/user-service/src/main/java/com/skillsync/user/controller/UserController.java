package com.skillsync.user.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.user.dto.AddUserSkillRequest;
import com.skillsync.user.dto.ApplyReferralRequest;
import com.skillsync.user.dto.AvatarResponse;
import com.skillsync.user.dto.PreferencesResponse;
import com.skillsync.user.dto.ReferralCodeResponse;
import com.skillsync.user.dto.ReferralResponse;
import com.skillsync.user.dto.UpdatePreferencesRequest;
import com.skillsync.user.dto.UpdateProfileRequest;
import com.skillsync.user.dto.UserProfileResponse;
import com.skillsync.user.dto.UserSkillResponse;
import com.skillsync.user.dto.UserSummaryResponse;
import com.skillsync.user.service.AvatarService;
import com.skillsync.user.service.PreferencesService;
import com.skillsync.user.service.ProfileService;
import com.skillsync.user.service.ReferralService;
import com.skillsync.user.service.UserSkillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final UserSkillService userSkillService;
    private final AvatarService avatarService;
    private final ReferralService referralService;

    public UserController(
            ProfileService profileService,
            PreferencesService preferencesService,
            UserSkillService userSkillService,
            AvatarService avatarService,
            ReferralService referralService
    ) {
        this.profileService = profileService;
        this.preferencesService = preferencesService;
        this.userSkillService = userSkillService;
        this.avatarService = avatarService;
        this.referralService = referralService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(HttpServletRequest request) {
        Long userId = extractUserId(request);
        UserProfileResponse response = profileService.getMyProfile(
                userId,
                request.getHeader("X-User-Email"),
                request.getHeader("X-User-Name"),
                extractRoles(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched successfully", response));
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getInternalUserById(@PathVariable Long id) {
        UserSummaryResponse response = profileService.getInternalUserSummary(id);
        return ResponseEntity.ok(ApiResponse.ok("Internal user fetched successfully", response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest updateProfileRequest
    ) {
        Long userId = extractUserId(request);
        profileService.updateProfile(userId, updateProfileRequest);
        UserProfileResponse response = profileService.getMyProfile(
                userId,
                request.getHeader("X-User-Email"),
                request.getHeader("X-User-Name"),
                extractRoles(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", response));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarResponse>> uploadAvatar(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        AvatarResponse response = avatarService.uploadAvatar(extractUserId(request), file);
        return ResponseEntity.ok(ApiResponse.ok("Avatar uploaded successfully", response));
    }

    @GetMapping("/me/skills")
    public ResponseEntity<ApiResponse<List<UserSkillResponse>>> getSkills(HttpServletRequest request) {
        List<UserSkillResponse> response = userSkillService.getUserSkills(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Skills fetched successfully", response));
    }

    @PostMapping("/me/skills")
    public ResponseEntity<ApiResponse<UserSkillResponse>> addSkill(
            HttpServletRequest request,
            @Valid @RequestBody AddUserSkillRequest addUserSkillRequest
    ) {
        UserSkillResponse response = userSkillService.addSkill(extractUserId(request), addUserSkillRequest);
        return ResponseEntity.ok(ApiResponse.ok("Skill added successfully", response));
    }

    @DeleteMapping("/me/skills/{skillId}")
    public ResponseEntity<ApiResponse<Void>> removeSkill(HttpServletRequest request, @PathVariable Long skillId) {
        userSkillService.removeSkill(extractUserId(request), skillId);
        return ResponseEntity.ok(ApiResponse.ok("Skill removed successfully", null));
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> getPreferences(HttpServletRequest request) {
        PreferencesResponse response = preferencesService.getPreferences(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Preferences fetched successfully", response));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> updatePreferences(
            HttpServletRequest request,
            @Valid @RequestBody UpdatePreferencesRequest updatePreferencesRequest
    ) {
        PreferencesResponse response = preferencesService.updatePreferences(
                extractUserId(request),
                updatePreferencesRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Preferences updated successfully", response));
    }

    @GetMapping("/me/referral-code")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> getReferralCode(HttpServletRequest request) {
        ReferralCodeResponse response = referralService.getReferralCode(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Referral code fetched successfully", response));
    }

    @PostMapping("/apply-referral")
    public ResponseEntity<ApiResponse<ReferralResponse>> applyReferral(
            HttpServletRequest request,
            @Valid @RequestBody ApplyReferralRequest applyReferralRequest
    ) {
        ReferralResponse response = referralService.applyReferralCode(extractUserId(request), applyReferralRequest);
        return ResponseEntity.ok(ApiResponse.ok("Referral applied successfully", response));
    }

    private Long extractUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userIdStr)) {
            throw new BadRequestException("X-User-Id header is required");
        }
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid X-User-Id header");
        }
    }

    private List<String> extractRoles(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-User-Roles");
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
