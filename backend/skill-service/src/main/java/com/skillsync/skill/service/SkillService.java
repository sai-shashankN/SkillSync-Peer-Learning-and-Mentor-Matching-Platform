package com.skillsync.skill.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.skill.dto.CreateSkillRequest;
import com.skillsync.skill.dto.SkillResponse;
import com.skillsync.skill.dto.UpdateSkillRequest;
import com.skillsync.skill.mapper.SkillMapper;
import com.skillsync.skill.model.Skill;
import com.skillsync.skill.model.SkillCategory;
import com.skillsync.skill.repository.SkillCategoryRepository;
import com.skillsync.skill.repository.SkillRepository;
import com.skillsync.skill.util.SlugUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final CategoryService categoryService;
    private final SkillMapper skillMapper;

    @Transactional
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public SkillResponse createSkill(CreateSkillRequest request) {
        String name = requireText(request.getName(), "Skill name is required");
        String slug = resolveSlug(request.getSlug(), name);

        if (skillRepository.existsByName(name)) {
            throw new ConflictException("Skill name already exists");
        }
        if (skillRepository.existsBySlug(slug)) {
            throw new ConflictException("Skill slug already exists");
        }

        SkillCategory category = categoryService.getActiveCategory(request.getCategoryId());
        Skill skill = Skill.builder()
                .name(name)
                .categoryId(category.getId())
                .description(normalizeDescription(request.getDescription(), false))
                .slug(slug)
                .isActive(true)
                .build();
        Skill savedSkill = skillRepository.save(skill);
        return skillMapper.toSkillResponse(savedSkill, category.getName());
    }

    @Transactional(readOnly = true)
    public SkillResponse getSkillById(Long id) {
        Skill skill = getActiveSkill(id);
        return skillMapper.toSkillResponse(skill, resolveCategoryName(skill.getCategoryId()));
    }

    @Transactional(readOnly = true)
    @Cacheable("skills")
    public List<SkillResponse> getAllSkills(Integer categoryId, String search) {
        List<Skill> skills;
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;

        if (categoryId != null) {
            skills = skillRepository.findByCategoryIdAndIsActiveTrueOrderByNameAsc(categoryId);
        } else if (normalizedSearch != null) {
            skills = skillRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(normalizedSearch);
        } else {
            skills = skillRepository.findByIsActiveTrueOrderByNameAsc();
        }

        return mapSkills(skills);
    }

    @Transactional
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public SkillResponse updateSkill(Long id, UpdateSkillRequest request) {
        Skill skill = getExistingSkill(id);

        if (request.getName() != null) {
            String name = requireText(request.getName(), "Skill name must not be blank");
            if (!name.equals(skill.getName()) && skillRepository.existsByName(name)) {
                throw new ConflictException("Skill name already exists");
            }
            skill.setName(name);
        }

        if (request.getCategoryId() != null) {
            SkillCategory category = categoryService.getActiveCategory(request.getCategoryId());
            skill.setCategoryId(category.getId());
        }

        if (request.getDescription() != null) {
            skill.setDescription(normalizeDescription(request.getDescription(), true));
        }

        if (request.getSlug() != null) {
            String slug = resolveSlug(request.getSlug(), request.getSlug());
            if (!slug.equals(skill.getSlug()) && skillRepository.existsBySlug(slug)) {
                throw new ConflictException("Skill slug already exists");
            }
            skill.setSlug(slug);
        }

        if (request.getIsActive() != null) {
            skill.setIsActive(request.getIsActive());
        }

        Skill savedSkill = skillRepository.save(skill);
        return skillMapper.toSkillResponse(savedSkill, resolveCategoryName(savedSkill.getCategoryId()));
    }

    @Transactional
    @CacheEvict(cacheNames = "skills", allEntries = true)
    public void deleteSkill(Long id) {
        Skill skill = getExistingSkill(id);
        skill.setIsActive(false);
        skillRepository.save(skill);
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> getSkillsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Skill> skills = skillRepository.findByIdIn(ids).stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getIsActive()))
                .toList();
        return mapSkills(skills);
    }

    @Transactional(readOnly = true)
    public Skill getExistingSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill", "id", id));
    }

    private Skill getActiveSkill(Long id) {
        Skill skill = getExistingSkill(id);
        if (!Boolean.TRUE.equals(skill.getIsActive())) {
            throw new ResourceNotFoundException("Skill", "id", id);
        }
        return skill;
    }

    private List<SkillResponse> mapSkills(List<Skill> skills) {
        Map<Integer, String> categoryNames = loadCategoryNames(skills.stream()
                .map(Skill::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return skills.stream()
                .map(skill -> skillMapper.toSkillResponse(skill, categoryNames.get(skill.getCategoryId())))
                .toList();
    }

    private Map<Integer, String> loadCategoryNames(Collection<Integer> categoryIds) {
        return skillCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(SkillCategory::getId, SkillCategory::getName, (left, right) -> left));
    }

    private String resolveCategoryName(Integer categoryId) {
        return skillCategoryRepository.findById(categoryId)
                .map(SkillCategory::getName)
                .orElse(null);
    }

    private String resolveSlug(String requestedSlug, String fallbackName) {
        if (StringUtils.hasText(requestedSlug)) {
            return SlugUtils.toSlug(requestedSlug.trim());
        }
        return SlugUtils.toSlug(fallbackName);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeDescription(String value, boolean allowNull) {
        if (value == null) {
            return null;
        }
        if (!StringUtils.hasText(value)) {
            if (allowNull) {
                return null;
            }
            throw new BadRequestException("Description must not be blank");
        }
        return value.trim();
    }
}
