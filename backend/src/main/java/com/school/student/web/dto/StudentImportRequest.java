package com.school.student.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentImportRequest(
        @NotBlank @Size(max = 5_000_000) String csv,
        boolean dryRun
) {
}
