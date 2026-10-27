package com.school.user.web.dto;

import com.school.user.domain.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UserRolesRequest(@NotEmpty Set<Role> roles) {
}
