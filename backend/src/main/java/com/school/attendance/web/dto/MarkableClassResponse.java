package com.school.attendance.web.dto;

public record MarkableClassResponse(
        Long classId,
        String className,
        String section,
        String academicYearName,
        String role
) {
}
