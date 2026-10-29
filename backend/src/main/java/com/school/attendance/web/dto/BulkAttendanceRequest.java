package com.school.attendance.web.dto;

import com.school.attendance.domain.AttendanceSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record BulkAttendanceRequest(
        @NotNull LocalDate date,
        AttendanceSource source,
        Boolean overrideHoliday,
        @NotEmpty @Size(max = 2000) @Valid List<BulkAttendanceEntry> entries
) {
}
