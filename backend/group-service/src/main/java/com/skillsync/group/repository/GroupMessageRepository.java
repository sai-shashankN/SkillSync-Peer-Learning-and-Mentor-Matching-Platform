package com.skillsync.group.repository;

import com.skillsync.group.model.GroupMessage;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    Page<GroupMessage> findByGroupIdAndIsDeletedFalse(Long groupId, Pageable pageable);

    Optional<GroupMessage> findByIdAndGroupId(Long id, Long groupId);
}
