package com.school.attendance.web.dto;

public record AttendanceCounts(
        int total,
        int present,
        int absent,
        int late,
        int halfDay,
        int excused,
        int unmarked
) {
}
