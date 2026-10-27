package com.school.student.web.dto;

import com.school.student.domain.StudentStatus;

import java.time.LocalDate;

public record StudentResponse(
        Long id,
        String fullName,
        String admissionNo,
        LocalDate dob,
        String photoPath,
        LocalDate admissionDate,
        StudentStatus status,
        Long currentClassId,
        String currentClassName,
        String currentSection,
        Integer rollNo
) {
}
