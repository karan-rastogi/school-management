package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceStatus;

import java.time.LocalDate;

/** A missed day: a full absence or a half day, on a working day within the counted window. */
public record ChildAbsence(
        LocalDate date,
        AttendanceStatus status
) {
}
