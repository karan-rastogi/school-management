package com.school.academic.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SchoolClassRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String section,
        @NotNull Long academicYearId
) {
}
