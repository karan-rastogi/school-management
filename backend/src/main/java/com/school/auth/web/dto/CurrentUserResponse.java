package com.school.auth.web.dto;

import com.school.auth.security.AuthenticatedUser;
import com.school.user.domain.Role;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        Set<Role> roles,
        Role activeRole,
        boolean mustChangePassword
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.id(),
                user.name(),
                user.email(),
                user.phone(),
                user.roles(),
                user.activeRole(),
                user.mustChangePassword());
    }
}
