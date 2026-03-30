package com.skillsync.group.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.group.dto.MessageRequest;
import com.skillsync.group.dto.MessageResponse;
import com.skillsync.group.model.Group;
import com.skillsync.group.model.GroupMember;
import com.skillsync.group.model.GroupMessage;
import com.skillsync.group.model.enums.GroupMemberRole;
import com.skillsync.group.repository.GroupMemberRepository;
import com.skillsync.group.repository.GroupMessageRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final GroupService groupService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;

    @Transactional
    public MessageResponse sendMessage(Long groupId, Long userId, String senderName, MessageRequest request) {
        Group group = groupService.getRequiredGroup(groupId);
        assertActiveGroup(group);
        requireMembership(groupId, userId);

        GroupMessage message = groupMessageRepository.save(GroupMessage.builder()
                .groupId(groupId)
                .userId(userId)
                .senderName(resolveSenderName(senderName, userId))
                .content(request.getContent().trim())
                .build());
        return toMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MessageResponse> getMessages(Long groupId, Long userId, Pageable pageable) {
        requireMembership(groupId, userId);
        Page<GroupMessage> page = groupMessageRepository.findByGroupIdAndIsDeletedFalse(groupId, pageable);
        return PagedResponse.<MessageResponse>builder()
                .content(page.getContent().stream().map(this::toMessageResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public void deleteMessage(Long groupId, Long messageId, Long userId, boolean isAdmin) {
        GroupMessage message = groupMessageRepository.findByIdAndGroupId(messageId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMessage", "id", messageId));

        if (!isAdmin && !message.getUserId().equals(userId)) {
            GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                    .orElseThrow(() -> new UnauthorizedException("You are not allowed to delete this message"));
            Set<String> elevatedRoles = Set.of(GroupMemberRole.CREATOR.name(), GroupMemberRole.MODERATOR.name());
            if (!elevatedRoles.contains(member.getRole())) {
                throw new UnauthorizedException("You are not allowed to delete this message");
            }
        }

        message.setIsDeleted(true);
        groupMessageRepository.save(message);
    }

    private void requireMembership(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new UnauthorizedException("You must be a member of the group");
        }
    }

    private void assertActiveGroup(Group group) {
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            throw new BadRequestException("This group is inactive");
        }
    }

    private MessageResponse toMessageResponse(GroupMessage message) {
        return MessageResponse.builder()
                .id(message.getId())
                .groupId(message.getGroupId())
                .senderId(message.getUserId())
                .senderName(resolveSenderName(message.getSenderName(), message.getUserId()))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isDeleted(Boolean.TRUE.equals(message.getIsDeleted()))
                .build();
    }

    private String resolveSenderName(String senderName, Long userId) {
        if (StringUtils.hasText(senderName)) {
            return senderName.trim();
        }

        return "User #" + userId;
    }
}
