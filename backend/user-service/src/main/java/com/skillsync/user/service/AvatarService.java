package com.skillsync.user.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.user.dto.AvatarResponse;
import com.skillsync.user.model.Profile;
import com.skillsync.user.repository.ProfileRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final CloudinaryService cloudinaryService;

    public AvatarService(
            ProfileRepository profileRepository,
            ProfileService profileService,
            CloudinaryService cloudinaryService
    ) {
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional
    public AvatarResponse uploadAvatar(Long userId, MultipartFile file) {
        validateFile(file);
        Profile profile = profileService.getRequiredProfile(userId);
        String avatarUrl = cloudinaryService.uploadImage(file, "skillsync/avatars");
        profile.setAvatarUrl(avatarUrl);
        profileRepository.save(profile);
        return AvatarResponse.builder().avatarUrl(avatarUrl).build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Avatar file size must not exceed 5MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Avatar file must be jpg, png, or webp");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("Avatar filename is invalid");
        }
    }
}
