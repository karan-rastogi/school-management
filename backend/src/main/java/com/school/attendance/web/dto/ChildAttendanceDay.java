package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceStatus;
import com.school.calendar.domain.SchoolCalendarDayType;

import java.time.LocalDate;

/**
 * One cell of a child's monthly attendance calendar.
 *
 * <ul>
 *   <li>{@code working} — whether the date is a school day (holidays and weekends are not).</li>
 *   <li>{@code calendarType} — the explicit school-calendar override for the date, if any.</li>
 *   <li>{@code status} — the attendance status recorded for the child, or {@code null} if none.</li>
 *   <li>{@code counted} — whether the day is included in the percentage denominator.</li>
 *   <li>{@code future} — whether the date is after today.</li>
 * </ul>
 */
public record ChildAttendanceDay(
        LocalDate date,
        boolean working,
        SchoolCalendarDayType calendarType,
        AttendanceStatus status,
        boolean counted,
        boolean future
) {
}
