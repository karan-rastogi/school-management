package com.school.calendar.service;

import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.calendar.web.dto.CalendarDayResponse;
import com.school.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchoolCalendarServiceTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 1, 3);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 1, 4);
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 1, 6);
    private static final LocalDate WEEK_END = LocalDate.of(2026, 1, 11);

    private SchoolCalendarRepository schoolCalendarRepository;
    private SchoolCalendarService schoolCalendarService;

    @BeforeEach
    void setUp() {
        schoolCalendarRepository = mock(SchoolCalendarRepository.class);
        schoolCalendarService = new SchoolCalendarService(schoolCalendarRepository);
        when(schoolCalendarRepository.findById(any())).thenReturn(Optional.empty());
        when(schoolCalendarRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
    }

    @Test
    void sundayIsNotAWorkingDayByDefault() {
        assertThat(schoolCalendarService.isWorkingDay(SUNDAY)).isFalse();
    }

    @Test
    void saturdayIsNotAWorkingDayByDefault() {
        assertThat(schoolCalendarService.isWorkingDay(SATURDAY)).isFalse();
    }

    @Test
    void aWeekdayIsAWorkingDayByDefault() {
        assertThat(schoolCalendarService.isWorkingDay(MONDAY)).isTrue();
    }

    @Test
    void aSundayCanBeOverriddenIntoAWorkingDay() {
        when(schoolCalendarRepository.findById(SUNDAY))
                .thenReturn(Optional.of(entry(SUNDAY, SchoolCalendarDayType.WORKING)));

        assertThat(schoolCalendarService.isWorkingDay(SUNDAY)).isTrue();
    }

    @Test
    void aWorkingSaturdayIsAWorkingDay() {
        when(schoolCalendarRepository.findById(SATURDAY))
                .thenReturn(Optional.of(entry(SATURDAY, SchoolCalendarDayType.WORKING)));

        assertThat(schoolCalendarService.isWorkingDay(SATURDAY)).isTrue();
    }

    @Test
    void aHolidayMakesAWeekdayNonWorking() {
        when(schoolCalendarRepository.findById(MONDAY))
                .thenReturn(Optional.of(entry(MONDAY, SchoolCalendarDayType.HOLIDAY)));

        assertThat(schoolCalendarService.isWorkingDay(MONDAY)).isFalse();
    }

    @Test
    void aHalfDayAndAnExamDayAreStillWorkingDays() {
        when(schoolCalendarRepository.findById(MONDAY))
                .thenReturn(Optional.of(entry(MONDAY, SchoolCalendarDayType.HALF_DAY)));
        when(schoolCalendarRepository.findById(TUESDAY))
                .thenReturn(Optional.of(entry(TUESDAY, SchoolCalendarDayType.EXAM)));

        assertThat(schoolCalendarService.isWorkingDay(MONDAY)).isTrue();
        assertThat(schoolCalendarService.isWorkingDay(TUESDAY)).isTrue();
    }

    @Test
    void workingDaysBetweenCountsWeekdaysInclusive() {
        assertThat(schoolCalendarService.workingDaysBetween(MONDAY, WEEK_END)).isEqualTo(5);
    }

    @Test
    void workingDaysBetweenExcludesHolidaysAndAddsWorkingSaturdays() {
        when(schoolCalendarRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(
                entry(TUESDAY, SchoolCalendarDayType.HOLIDAY),
                entry(LocalDate.of(2026, 1, 10), SchoolCalendarDayType.WORKING)));

        assertThat(schoolCalendarService.workingDaysBetween(MONDAY, WEEK_END)).isEqualTo(5);
    }

    @Test
    void workingDaysBetweenASingleWorkingDayIsOne() {
        assertThat(schoolCalendarService.workingDaysBetween(MONDAY, MONDAY)).isEqualTo(1);
    }

    @Test
    void workingDaysBetweenASingleSundayIsZero() {
        assertThat(schoolCalendarService.workingDaysBetween(SUNDAY, SUNDAY)).isZero();
    }

    @Test
    void workingDaysBetweenRejectsAReverseRange() {
        assertThatThrownBy(() -> schoolCalendarService.workingDaysBetween(WEEK_END, MONDAY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void workingDaysBetweenRejectsAnOverlongRange() {
        assertThatThrownBy(() -> schoolCalendarService.workingDaysBetween(
                LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rangeMarksWeekendsAndExplicitEntries() {
        when(schoolCalendarRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(List.of(entry(TUESDAY, SchoolCalendarDayType.HOLIDAY)));

        List<CalendarDayResponse> days = schoolCalendarService.range(MONDAY, WEEK_END);

        assertThat(days).hasSize(7);
        assertThat(days.get(0).date()).isEqualTo(MONDAY);
        assertThat(days.get(0).working()).isTrue();
        assertThat(days.get(0).type()).isNull();
        assertThat(days.get(1).type()).isEqualTo(SchoolCalendarDayType.HOLIDAY);
        assertThat(days.get(1).working()).isFalse();
        assertThat(days.get(5).working()).isFalse();
        assertThat(days.get(6).working()).isFalse();
    }

    private static SchoolCalendarEntry entry(LocalDate date, SchoolCalendarDayType type) {
        SchoolCalendarEntry entry = new SchoolCalendarEntry();
        entry.setDate(date);
        entry.setType(type);
        return entry;
    }
}
