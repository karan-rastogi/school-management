package com.school.calendar.web.dto;

import java.time.LocalDate;

public record WorkingDaysResponse(
        LocalDate from,
        LocalDate to,
        long workingDays
) {
}
