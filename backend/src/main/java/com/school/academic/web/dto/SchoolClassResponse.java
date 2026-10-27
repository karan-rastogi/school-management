package com.school.academic.web.dto;

public record SchoolClassResponse(
        Long id,
        String name,
        String section,
        Long academicYearId,
        String academicYearName
) {
}
