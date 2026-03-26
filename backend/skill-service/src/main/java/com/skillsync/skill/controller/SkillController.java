package com.skillsync.skill.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.skill.dto.CategoryResponse;
import com.skillsync.skill.dto.CreateSkillRequest;
import com.skillsync.skill.dto.SkillResponse;
import com.skillsync.skill.dto.UpdateSkillRequest;
import com.skillsync.skill.service.CategoryService;
import com.skillsync.skill.service.SkillService;
import com.skillsync.skill.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(
            HttpServletRequest request,
            @Valid @RequestBody CreateSkillRequest createSkillRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Skill created successfully", skillService.createSkill(createSkillRequest)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillResponse>>> getSkills(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Skills fetched successfully", skillService.getAllSkills(categoryId, search)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> getSkillById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Skill fetched successfully", skillService.getSkillById(id)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Categories fetched successfully", categoryService.getAllCategories()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> updateSkill(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSkillRequest updateSkillRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Skill updated successfully", skillService.updateSkill(id, updateSkillRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(HttpServletRequest request, @PathVariable Long id) {
        RequestHeaderUtils.requireAdmin(request);
        skillService.deleteSkill(id);
        return ResponseEntity.ok(ApiResponse.ok("Skill deleted successfully", null));
    }

    @GetMapping("/internal/validate")
    public ResponseEntity<ApiResponse<List<SkillResponse>>> validateSkills(@RequestParam(name = "ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok("Skills validated successfully", skillService.getSkillsByIds(ids)));
    }
}
