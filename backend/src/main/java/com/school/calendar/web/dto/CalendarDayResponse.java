package com.school.calendar.web.dto;

import com.school.calendar.domain.SchoolCalendarDayType;

import java.time.LocalDate;

public record CalendarDayResponse(
        LocalDate date,
        SchoolCalendarDayType type,
        boolean working,
        String description
) {
}
