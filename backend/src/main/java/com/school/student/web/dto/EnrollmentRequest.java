package com.school.student.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EnrollmentRequest(
        @NotNull Long classId,
        @Min(1) Integer rollNo,
        @NotNull LocalDate fromDate,
        LocalDate toDate
) {
}
