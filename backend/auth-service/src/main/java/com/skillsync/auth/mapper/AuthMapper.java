package com.skillsync.auth.mapper;

import com.skillsync.auth.dto.UserInfo;
import com.skillsync.auth.model.Role;
import com.skillsync.auth.model.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "roles", expression = "java(extractRoles(user))")
    @Mapping(target = "permissions", expression = "java(extractPermissions(user))")
    UserInfo toUserInfo(User user);

    default List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    default List<String> extractPermissions(User user) {
        return new ArrayList<>(user.getPermissionNames().stream()
                .sorted(Comparator.naturalOrder())
                .toList());
    }
}
