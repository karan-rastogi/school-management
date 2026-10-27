package com.school.teacher.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SubstitutePermissionRequest(
        @NotNull Long teacherUserId,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @Size(max = 500) String reason
) {
}
