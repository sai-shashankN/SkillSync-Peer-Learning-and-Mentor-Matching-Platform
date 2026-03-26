package com.skillsync.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_badges")
@IdClass(UserBadgeId.class)
public class UserBadge {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "badge_id", nullable = false)
    private Integer badgeId;

    @Column(name = "awarded_for_skill_id")
    private Long awardedForSkillId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "badge_id", insertable = false, updatable = false)
    private Badge badge;

    @PrePersist
    public void prePersist() {
        if (earnedAt == null) {
            earnedAt = Instant.now();
        }
    }
}
