package com.skillsync.group.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private Long groupId;
    private Long senderId;
    private String senderName;
    private String content;
    private Instant createdAt;
    private Boolean isDeleted;
}
