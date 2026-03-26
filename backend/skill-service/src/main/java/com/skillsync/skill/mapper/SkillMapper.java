package com.skillsync.skill.mapper;

import com.skillsync.skill.dto.CategoryResponse;
import com.skillsync.skill.dto.SkillResponse;
import com.skillsync.skill.model.Skill;
import com.skillsync.skill.model.SkillCategory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SkillMapper {

    default SkillResponse toSkillResponse(Skill skill, String categoryName) {
        return SkillResponse.builder()
                .id(skill.getId())
                .name(skill.getName())
                .categoryId(skill.getCategoryId())
                .categoryName(categoryName)
                .description(skill.getDescription())
                .slug(skill.getSlug())
                .isActive(skill.getIsActive())
                .createdAt(skill.getCreatedAt())
                .updatedAt(skill.getUpdatedAt())
                .build();
    }

    CategoryResponse toCategoryResponse(SkillCategory category);
}
