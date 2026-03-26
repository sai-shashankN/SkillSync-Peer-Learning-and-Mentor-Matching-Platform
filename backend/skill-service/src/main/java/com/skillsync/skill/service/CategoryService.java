package com.skillsync.skill.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.skill.dto.CategoryResponse;
import com.skillsync.skill.dto.CreateCategoryRequest;
import com.skillsync.skill.dto.UpdateCategoryRequest;
import com.skillsync.skill.mapper.SkillMapper;
import com.skillsync.skill.model.SkillCategory;
import com.skillsync.skill.repository.SkillCategoryRepository;
import com.skillsync.skill.util.SlugUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final SkillCategoryRepository skillCategoryRepository;
    private final SkillMapper skillMapper;

    @Transactional
    @CacheEvict(cacheNames = {"categories", "skills"}, allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = requireText(request.getName(), "Category name is required");
        String slug = resolveSlug(request.getSlug(), name);

        if (skillCategoryRepository.existsByName(name)) {
            throw new ConflictException("Category name already exists");
        }
        if (skillCategoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category slug already exists");
        }

        SkillCategory category = SkillCategory.builder()
                .name(name)
                .slug(slug)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(true)
                .build();
        return skillMapper.toCategoryResponse(skillCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    @Cacheable("categories")
    public List<CategoryResponse> getAllCategories() {
        return skillCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(skillMapper::toCategoryResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories", "skills"}, allEntries = true)
    public CategoryResponse updateCategory(Integer id, UpdateCategoryRequest request) {
        SkillCategory category = getExistingCategory(id);

        if (request.getName() != null) {
            String name = requireText(request.getName(), "Category name must not be blank");
            if (!name.equals(category.getName()) && skillCategoryRepository.existsByName(name)) {
                throw new ConflictException("Category name already exists");
            }
            category.setName(name);
        }

        if (request.getSlug() != null) {
            String slug = resolveSlug(request.getSlug(), request.getSlug());
            if (!slug.equals(category.getSlug()) && skillCategoryRepository.existsBySlug(slug)) {
                throw new ConflictException("Category slug already exists");
            }
            category.setSlug(slug);
        }

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        return skillMapper.toCategoryResponse(skillCategoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories", "skills"}, allEntries = true)
    public void deleteCategory(Integer id) {
        SkillCategory category = getExistingCategory(id);
        category.setIsActive(false);
        skillCategoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public SkillCategory getExistingCategory(Integer id) {
        return skillCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SkillCategory", "id", id));
    }

    @Transactional(readOnly = true)
    public SkillCategory getActiveCategory(Integer id) {
        SkillCategory category = getExistingCategory(id);
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new BadRequestException("Category is inactive");
        }
        return category;
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
}
