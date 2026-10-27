package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record BulkAttendanceEntry(
        @NotNull Long studentId,
        @NotNull AttendanceStatus status
) {
}
