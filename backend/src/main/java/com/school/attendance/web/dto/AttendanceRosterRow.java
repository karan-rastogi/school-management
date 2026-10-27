package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;

import java.time.OffsetDateTime;

public record AttendanceRosterRow(
        Long studentId,
        String fullName,
        String admissionNo,
        String photoPath,
        Integer rollNo,
        AttendanceStatus status,
        OffsetDateTime markedAt,
        AttendanceSource source
) {
}
