package com.school.attendance.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceScanRequest(
        @NotNull UUID qrToken,
        @NotNull Long classId,
        @NotNull LocalDate date
) {
}
