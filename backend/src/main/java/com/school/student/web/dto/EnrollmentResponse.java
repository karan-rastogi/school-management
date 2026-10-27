package com.school.student.web.dto;

import java.time.LocalDate;

public record EnrollmentResponse(
        Long id,
        Long classId,
        String className,
        String section,
        String academicYearName,
        Integer rollNo,
        LocalDate fromDate,
        LocalDate toDate
) {
}
