package com.school.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveAttendanceQueryRequest(
        @NotBlank @Size(max = 2000) String response
) {
}
