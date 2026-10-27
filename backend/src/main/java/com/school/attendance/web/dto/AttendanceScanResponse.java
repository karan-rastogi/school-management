package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceStatus;

import java.time.OffsetDateTime;

public record AttendanceScanResponse(
        Long studentId,
        String fullName,
        String photoPath,
        AttendanceStatus status,
        OffsetDateTime markedAt,
        boolean marked,
        boolean alreadyMarked,
        String warning,
        String message
) {
}
