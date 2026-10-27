package com.school.student.web.dto;

import com.school.student.domain.StudentStatus;

public record ChildResponse(
        Long studentId,
        String fullName,
        String admissionNo,
        StudentStatus status,
        String relationship,
        Long classId,
        String className,
        String section
) {
}
