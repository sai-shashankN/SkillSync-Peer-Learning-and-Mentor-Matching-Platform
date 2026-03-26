package com.skillsync.group.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.group.dto.CreateGroupRequest;
import com.skillsync.group.dto.GroupDetailResponse;
import com.skillsync.group.dto.GroupMemberResponse;
import com.skillsync.group.dto.GroupResponse;
import com.skillsync.group.dto.GroupSummaryResponse;
import com.skillsync.group.mapper.GroupMapper;
import com.skillsync.group.model.Group;
import com.skillsync.group.model.GroupMember;
import com.skillsync.group.model.enums.GroupMemberRole;
import com.skillsync.group.repository.GroupMemberRepository;
import com.skillsync.group.repository.GroupRepository;
import com.skillsync.group.util.SlugUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMapper groupMapper;

    @Transactional
    public GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        Group group = groupRepository.save(Group.builder()
                .name(request.getName().trim())
                .slug(generateSlug(request.getName()))
                .description(request.getDescription())
                .createdBy(userId)
                .maxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 50)
                .skillIds(request.getSkillIds() != null ? new LinkedHashSet<>(request.getSkillIds()) : new LinkedHashSet<>())
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .role(GroupMemberRole.CREATOR.name())
                .build());

        return toGroupResponse(group, 1L);
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupById(Long groupId) {
        Group group = getRequiredActiveOrInactiveGroup(groupId);
        List<GroupMemberResponse> members = groupMemberRepository.findByGroupId(groupId).stream()
                .map(groupMapper::toGroupMemberResponse)
                .toList();

        GroupDetailResponse response = groupMapper.toGroupDetailResponse(group);
        response.setMemberCount((long) members.size());
        response.setMembers(members);
        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<GroupSummaryResponse> searchGroups(String search, Long skillId, Pageable pageable) {
        Page<Group> page = groupRepository.searchGroups(normalizeSearch(search), skillId, pageable);
        return PagedResponse.<GroupSummaryResponse>builder()
                .content(page.getContent().stream()
                        .map(group -> toGroupSummaryResponse(group, groupMemberRepository.countByGroupId(group.getId())))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> getMyGroups(Long userId) {
        return getUserGroupSummaries(userId);
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> getUserGroups(Long userId) {
        return getUserGroupSummaries(userId);
    }

    @Transactional
    public GroupMemberResponse joinGroup(Long groupId, Long userId) {
        Group group = getRequiredGroup(groupId);
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            throw new BadRequestException("This group is inactive");
        }
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("You are already a member of this group");
        }
        long memberCount = groupMemberRepository.countByGroupId(groupId);
        if (memberCount >= group.getMaxMembers()) {
            throw new BadRequestException("This group has reached its member limit");
        }

        GroupMember member = groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(GroupMemberRole.MEMBER.name())
                .build());
        return groupMapper.toGroupMemberResponse(member);
    }

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        GroupMember member = getRequiredMember(groupId, userId);
        if (GroupMemberRole.CREATOR.name().equals(member.getRole())) {
            throw new BadRequestException("Creators cannot leave the group. Delete the group instead");
        }
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Transactional
    public void deleteGroup(Long groupId, Long adminUserId) {
        Group group = getRequiredGroup(groupId);
        group.setIsActive(false);
        groupRepository.save(group);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId, Long requesterUserId, boolean isAdmin) {
        Group group = getRequiredGroup(groupId);
        if (!isAdmin) {
            GroupMember requester = getRequiredMember(groupId, requesterUserId);
            if (!GroupMemberRole.CREATOR.name().equals(requester.getRole())) {
                throw new UnauthorizedException("Only the creator or an admin can remove members");
            }
        }

        GroupMember targetMember = getRequiredMember(groupId, userId);
        if (GroupMemberRole.CREATOR.name().equals(targetMember.getRole())) {
            throw new BadRequestException("The creator cannot be removed from the group");
        }

        groupMemberRepository.deleteByGroupIdAndUserId(group.getId(), userId);
    }

    @Transactional(readOnly = true)
    public Group getRequiredGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
    }

    @Transactional(readOnly = true)
    public Group getRequiredActiveOrInactiveGroup(Long groupId) {
        return getRequiredGroup(groupId);
    }

    @Transactional(readOnly = true)
    public GroupMember getRequiredMember(Long groupId, Long userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId/userId", groupId + "/" + userId));
    }

    private List<GroupSummaryResponse> getUserGroupSummaries(Long userId) {
        return groupMemberRepository.findByUserId(userId).stream()
                .map(GroupMember::getGroup)
                .filter(group -> group != null && Boolean.TRUE.equals(group.getIsActive()))
                .map(group -> toGroupSummaryResponse(group, groupMemberRepository.countByGroupId(group.getId())))
                .toList();
    }

    private GroupResponse toGroupResponse(Group group, Long memberCount) {
        GroupResponse response = groupMapper.toGroupResponse(group);
        response.setMemberCount(memberCount);
        return response;
    }

    private GroupSummaryResponse toGroupSummaryResponse(Group group, Long memberCount) {
        GroupSummaryResponse response = groupMapper.toGroupSummaryResponse(group);
        response.setMemberCount(memberCount);
        return response;
    }

    private String normalizeSearch(String search) {
        return StringUtils.hasText(search) ? search.trim() : null;
    }

    private String generateSlug(String name) {
        String baseSlug = SlugUtils.slugify(name);
        if (!StringUtils.hasText(baseSlug)) {
            baseSlug = "group";
        }

        String slug = baseSlug;
        while (groupRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        }
        return slug;
    }
}
