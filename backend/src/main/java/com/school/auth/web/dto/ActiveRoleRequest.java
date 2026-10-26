package com.school.auth.web.dto;

import com.school.user.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ActiveRoleRequest(@NotNull Role role) {
}
