package com.skillsync.group.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.group.dto.CreateGroupRequest;
import com.skillsync.group.dto.GroupDetailResponse;
import com.skillsync.group.dto.GroupMemberResponse;
import com.skillsync.group.dto.GroupResponse;
import com.skillsync.group.dto.GroupSummaryResponse;
import com.skillsync.group.dto.MessageRequest;
import com.skillsync.group.dto.MessageResponse;
import com.skillsync.group.service.GroupService;
import com.skillsync.group.service.MessageService;
import com.skillsync.group.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            HttpServletRequest request,
            @Valid @RequestBody CreateGroupRequest createGroupRequest
    ) {
        GroupResponse response = groupService.createGroup(
                RequestHeaderUtils.extractUserId(request),
                createGroupRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Group created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<GroupSummaryResponse>>> searchGroups(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Groups fetched successfully",
                groupService.searchGroups(search, skillId, pageable)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<GroupSummaryResponse>>> getMyGroups(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Groups fetched successfully",
                groupService.getMyGroups(RequestHeaderUtils.extractUserId(request))
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<GroupSummaryResponse>>> getUserGroups(
            HttpServletRequest request,
            @PathVariable Long userId
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Groups fetched successfully", groupService.getUserGroups(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupDetailResponse>> getGroupById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Group fetched successfully", groupService.getGroupById(id)));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> joinGroup(HttpServletRequest request, @PathVariable Long id) {
        GroupMemberResponse response = groupService.joinGroup(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Joined group successfully", response));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(HttpServletRequest request, @PathVariable Long id) {
        groupService.leaveGroup(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Left group successfully", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(HttpServletRequest request, @PathVariable Long id) {
        RequestHeaderUtils.requireAdmin(request);
        groupService.deleteGroup(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Group deleted successfully", null));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        groupService.removeMember(
                id,
                userId,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.hasAdminRole(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Member removed successfully", null));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody MessageRequest messageRequest
    ) {
        MessageResponse response = messageService.sendMessage(
                id,
                RequestHeaderUtils.extractUserId(request),
                messageRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Message sent successfully", response));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> getMessages(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Messages fetched successfully",
                messageService.getMessages(id, RequestHeaderUtils.extractUserId(request), pageable)
        ));
    }

    @DeleteMapping("/{id}/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long messageId
    ) {
        messageService.deleteMessage(
                id,
                messageId,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.hasAdminRole(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Message deleted successfully", null));
    }
}
