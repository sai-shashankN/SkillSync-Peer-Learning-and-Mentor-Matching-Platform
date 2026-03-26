package com.skillsync.auth.repository;

import com.skillsync.auth.model.RefreshToken;
import com.skillsync.auth.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);

    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);
}
