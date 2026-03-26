package com.skillsync.group.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long createdBy;
    private Integer maxMembers;
    private Long memberCount;
    private Set<Long> skillIds;
    private Boolean isActive;
    private Instant createdAt;
    private List<GroupMemberResponse> members;
}
