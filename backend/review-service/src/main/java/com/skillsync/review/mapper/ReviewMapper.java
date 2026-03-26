package com.skillsync.review.mapper;

import com.skillsync.review.dto.BadgeResponse;
import com.skillsync.review.dto.ReviewResponse;
import com.skillsync.review.dto.UserBadgeResponse;
import com.skillsync.review.model.Badge;
import com.skillsync.review.model.Review;
import com.skillsync.review.model.UserBadge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toReviewResponse(Review review);

    BadgeResponse toBadgeResponse(Badge badge);

    @Mapping(target = "badgeName", source = "badge.name")
    @Mapping(target = "tier", source = "badge.tier")
    @Mapping(target = "iconUrl", source = "badge.iconUrl")
    @Mapping(target = "skillId", source = "badge.skillId")
    UserBadgeResponse toUserBadgeResponse(UserBadge userBadge);
}
