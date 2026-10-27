package com.school.user.web.dto;

import com.school.user.domain.Role;

import java.time.OffsetDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        boolean active,
        Set<Role> roles,
        OffsetDateTime createdAt
) {
}
