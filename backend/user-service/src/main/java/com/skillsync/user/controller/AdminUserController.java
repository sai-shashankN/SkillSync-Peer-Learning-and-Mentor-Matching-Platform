package com.skillsync.user.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.user.dto.UserStatusResponse;
import com.skillsync.user.dto.UserSummaryResponse;
import com.skillsync.user.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<UserSummaryResponse>> listUsers(
            HttpServletRequest request,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAdminRole(request);
        PagedResponse<UserSummaryResponse> response = adminUserService.listUsers(
                search,
                role,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserById(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        requireAdminRole(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "User fetched successfully",
                adminUserService.getUserById(id)
        ));
    }

    @PatchMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<UserStatusResponse>> banUser(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        requireAdminRole(request);
        return ResponseEntity.ok(ApiResponse.ok("User banned successfully", adminUserService.banUser(id)));
    }

    @PatchMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<UserStatusResponse>> unbanUser(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        requireAdminRole(request);
        return ResponseEntity.ok(ApiResponse.ok("User unbanned successfully", adminUserService.unbanUser(id)));
    }

    private void requireAdminRole(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-User-Roles");
        if (!StringUtils.hasText(rolesHeader)) {
            throw new UnauthorizedException("ROLE_ADMIN is required");
        }

        boolean isAdmin = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new UnauthorizedException("ROLE_ADMIN is required");
        }
    }
}
