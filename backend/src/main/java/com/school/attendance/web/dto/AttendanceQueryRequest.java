package com.school.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AttendanceQueryRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 1000) String message
) {
}
