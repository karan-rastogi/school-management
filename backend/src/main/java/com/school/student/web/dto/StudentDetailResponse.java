package com.school.student.web.dto;

import com.school.student.domain.StudentStatus;

import java.time.LocalDate;
import java.util.List;

public record StudentDetailResponse(
        Long id,
        String fullName,
        String admissionNo,
        LocalDate dob,
        String photoPath,
        String qrToken,
        LocalDate admissionDate,
        StudentStatus status,
        List<EnrollmentResponse> enrollments,
        List<StudentParentResponse> parents
) {
}
