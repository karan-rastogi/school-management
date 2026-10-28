package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceQueryStatus;
import com.school.attendance.domain.AttendanceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A parent's dispute about one attendance date, together with the teacher's resolution. The
 * disputed date's recorded status is included so a teacher can answer without looking it up.
 */
public record AttendanceQueryResponse(
        Long id,
        Long studentId,
        String studentName,
        String admissionNo,
        Long classId,
        String className,
        String section,
        LocalDate attendanceDate,
        AttendanceStatus attendanceStatus,
        AttendanceQueryStatus status,
        String message,
        String response,
        OffsetDateTime createdAt,
        String respondedByName,
        OffsetDateTime respondedAt
) {
}
