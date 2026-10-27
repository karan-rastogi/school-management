package com.school.calendar.web;

import com.school.calendar.service.SchoolCalendarService;
import com.school.calendar.web.dto.CalendarDayResponse;
import com.school.calendar.web.dto.CalendarEntryRequest;
import com.school.calendar.web.dto.WorkingDaysResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/calendar")
@PreAuthorize("hasRole('ADMIN')")
public class SchoolCalendarController {

    private final SchoolCalendarService schoolCalendarService;

    public SchoolCalendarController(SchoolCalendarService schoolCalendarService) {
        this.schoolCalendarService = schoolCalendarService;
    }

    @GetMapping
    public List<CalendarDayResponse> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return schoolCalendarService.range(from, to);
    }

    @GetMapping("/working-days")
    public WorkingDaysResponse workingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return new WorkingDaysResponse(from, to, schoolCalendarService.workingDaysBetween(from, to));
    }

    @GetMapping("/{date}")
    public CalendarDayResponse day(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return schoolCalendarService.day(date);
    }

    @PutMapping("/{date}")
    public CalendarDayResponse mark(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CalendarEntryRequest request
    ) {
        return schoolCalendarService.mark(date, request);
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        schoolCalendarService.clear(date);
    }
}
