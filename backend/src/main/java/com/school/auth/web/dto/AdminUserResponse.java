package com.school.auth.web.dto;

import com.school.user.domain.Role;

import java.util.Set;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        boolean active,
        Set<Role> roles
) {
}
