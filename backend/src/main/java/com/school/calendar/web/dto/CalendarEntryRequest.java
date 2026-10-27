package com.school.calendar.web.dto;

import com.school.calendar.domain.SchoolCalendarDayType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CalendarEntryRequest(
        @NotNull SchoolCalendarDayType type,
        @Size(max = 500) String description
) {
}
