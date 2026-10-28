package com.school.notice.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Admin action: turn a global notice into a HOLIDAY entry on the school calendar. */
public record HolidayFromNoticeRequest(
        @NotNull LocalDate date,
        @Size(max = 500) String description
) {
}
