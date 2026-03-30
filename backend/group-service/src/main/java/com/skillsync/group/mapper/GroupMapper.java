package com.skillsync.group.mapper;

import com.skillsync.group.dto.GroupDetailResponse;
import com.skillsync.group.dto.GroupMemberResponse;
import com.skillsync.group.dto.GroupResponse;
import com.skillsync.group.dto.GroupSummaryResponse;
import com.skillsync.group.model.Group;
import com.skillsync.group.model.GroupMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "memberCount", ignore = true)
    GroupResponse toGroupResponse(Group group);

    @Mapping(target = "memberCount", ignore = true)
    GroupSummaryResponse toGroupSummaryResponse(Group group);

    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "members", ignore = true)
    GroupDetailResponse toGroupDetailResponse(Group group);

    GroupMemberResponse toGroupMemberResponse(GroupMember member);
}
