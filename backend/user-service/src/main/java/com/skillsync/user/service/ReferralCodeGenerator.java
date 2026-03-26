package com.skillsync.user.service;

import com.skillsync.user.model.ReferralCode;
import com.skillsync.user.repository.ReferralCodeRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ReferralCodeGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;

    private final ReferralCodeRepository referralCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ReferralCodeGenerator(ReferralCodeRepository referralCodeRepository) {
        this.referralCodeRepository = referralCodeRepository;
    }

    public ReferralCode ensureReferralCode(Long userId) {
        return referralCodeRepository.findByUserId(userId)
                .orElseGet(() -> referralCodeRepository.save(
                        ReferralCode.builder()
                                .userId(userId)
                                .code(generateUniqueCode())
                                .build()
                ));
    }

    public String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (referralCodeRepository.findByCode(code).isPresent());
        return code;
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            builder.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }
}
