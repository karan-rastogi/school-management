package com.school.attendance.web.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A child's attendance summary: the percentage over the counted window, the monthly calendar, and
 * the list of absences.
 *
 * <p>{@code percentage} is {@code presentEquivalentDays / countedDays * 100}, rounded to one
 * decimal. When nothing has been counted yet it is reported as 100 so a fresh record never raises a
 * false warning; {@code belowThreshold} is only true once at least one day has been counted.
 */
public record ChildAttendanceResponse(
        Long studentId,
        String fullName,
        String admissionNo,
        String photoPath,
        Long classId,
        String className,
        String section,
        LocalDate fromDate,
        LocalDate toDate,
        YearMonth month,
        double percentage,
        double presentEquivalentDays,
        int countedDays,
        int presentDays,
        int absentDays,
        int lateDays,
        int halfDays,
        int excusedDays,
        double warningThresholdPercent,
        boolean belowThreshold,
        List<ChildAttendanceDay> days,
        List<ChildAbsence> absences
) {
}
