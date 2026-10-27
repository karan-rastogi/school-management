package com.school.attendance.web.dto;

import java.time.LocalDate;
import java.util.List;

public record AttendanceRosterResponse(
        Long classId,
        String className,
        String section,
        String academicYearName,
        LocalDate date,
        boolean workingDay,
        AttendanceCounts counts,
        List<AttendanceRosterRow> students
) {
}
