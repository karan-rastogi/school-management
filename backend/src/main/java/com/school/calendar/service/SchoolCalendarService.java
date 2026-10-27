package com.school.calendar.service;

import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.calendar.web.dto.CalendarDayResponse;
import com.school.calendar.web.dto.CalendarEntryRequest;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchoolCalendarService {

    // Saturdays and Sundays are non-working unless a calendar entry overrides them.
    // A WORKING entry on a Saturday is what the UI calls a "working Saturday"; the
    // same override lets an exceptional Sunday become a working day.
    private static final Set<DayOfWeek> DEFAULT_NON_WORKING_DAYS =
            EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private static final long MAX_RANGE_DAYS = 366;

    private final SchoolCalendarRepository schoolCalendarRepository;

    public SchoolCalendarService(SchoolCalendarRepository schoolCalendarRepository) {
        this.schoolCalendarRepository = schoolCalendarRepository;
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(LocalDate date) {
        return schoolCalendarRepository.findById(date)
                .map(entry -> isWorking(entry.getType()))
                .orElseGet(() -> isWorkingByDefault(date));
    }

    @Transactional(readOnly = true)
    public long workingDaysBetween(LocalDate from, LocalDate to) {
        requireValidRange(from, to);
        Map<LocalDate, SchoolCalendarDayType> overrides = overridesBetween(from, to);

        long working = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isWorkingDay(date, overrides)) {
                working++;
            }
        }
        return working;
    }

    @Transactional(readOnly = true)
    public List<CalendarDayResponse> range(LocalDate from, LocalDate to) {
        requireValidRange(from, to);
        Map<LocalDate, SchoolCalendarEntry> entries = schoolCalendarRepository
                .findByDateBetweenOrderByDateAsc(from, to).stream()
                .collect(Collectors.toMap(SchoolCalendarEntry::getDate, Function.identity()));

        List<CalendarDayResponse> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add(toResponse(date, entries.get(date)));
        }
        return days;
    }

    @Transactional(readOnly = true)
    public CalendarDayResponse day(LocalDate date) {
        return toResponse(date, schoolCalendarRepository.findById(date).orElse(null));
    }

    @Transactional
    public CalendarDayResponse mark(LocalDate date, CalendarEntryRequest request) {
        SchoolCalendarEntry entry = schoolCalendarRepository.findById(date).orElseGet(() -> {
            SchoolCalendarEntry created = new SchoolCalendarEntry();
            created.setDate(date);
            return created;
        });

        entry.setType(request.type());
        entry.setDescription(blankToNull(request.description()));
        return toResponse(date, schoolCalendarRepository.saveAndFlush(entry));
    }

    @Transactional
    public void clear(LocalDate date) {
        schoolCalendarRepository.findById(date).ifPresent(schoolCalendarRepository::delete);
    }

    private Map<LocalDate, SchoolCalendarDayType> overridesBetween(LocalDate from, LocalDate to) {
        return schoolCalendarRepository.findByDateBetweenOrderByDateAsc(from, to).stream()
                .collect(Collectors.toMap(SchoolCalendarEntry::getDate, SchoolCalendarEntry::getType));
    }

    private static boolean isWorkingDay(LocalDate date, Map<LocalDate, SchoolCalendarDayType> overrides) {
        SchoolCalendarDayType type = overrides.get(date);
        return type == null ? isWorkingByDefault(date) : isWorking(type);
    }

    private static boolean isWorkingByDefault(LocalDate date) {
        return !DEFAULT_NON_WORKING_DAYS.contains(date.getDayOfWeek());
    }

    // WORKING, HALF_DAY and EXAM are all school days; only a full HOLIDAY is not.
    private static boolean isWorking(SchoolCalendarDayType type) {
        return type != SchoolCalendarDayType.HOLIDAY;
    }

    static CalendarDayResponse toResponse(LocalDate date, SchoolCalendarEntry entry) {
        SchoolCalendarDayType type = entry == null ? null : entry.getType();
        boolean working = type == null ? isWorkingByDefault(date) : isWorking(type);
        return new CalendarDayResponse(date, type, working, entry == null ? null : entry.getDescription());
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Provide both a from and a to date.");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The to date cannot be before the from date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Choose a range of at most " + MAX_RANGE_DAYS + " days.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
