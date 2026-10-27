package com.school.teacher.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SubstitutePermissionResponse(
        Long id,
        Long teacherUserId,
        String teacherName,
        Long classId,
        String className,
        String section,
        String academicYearName,
        LocalDate validFrom,
        LocalDate validUntil,
        String reason,
        OffsetDateTime grantedAt
) {
}
