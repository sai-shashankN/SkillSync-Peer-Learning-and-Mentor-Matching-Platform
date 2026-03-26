package com.skillsync.skill.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.skill.dto.CategoryResponse;
import com.skillsync.skill.dto.CreateCategoryRequest;
import com.skillsync.skill.dto.UpdateCategoryRequest;
import com.skillsync.skill.service.CategoryService;
import com.skillsync.skill.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            HttpServletRequest request,
            @Valid @RequestBody CreateCategoryRequest createCategoryRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Category created successfully", categoryService.createCategory(createCategoryRequest)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            HttpServletRequest request,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCategoryRequest updateCategoryRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Category updated successfully", categoryService.updateCategory(id, updateCategoryRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(HttpServletRequest request, @PathVariable Integer id) {
        RequestHeaderUtils.requireAdmin(request);
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted successfully", null));
    }
}
