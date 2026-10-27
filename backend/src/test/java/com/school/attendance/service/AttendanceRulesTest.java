package com.school.attendance.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Test
    void todayAndYesterdayAreWithinTheClassTeacherWindow() {
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY, TODAY)).isFalse();
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY.minusDays(2), TODAY)).isFalse();
    }

    @Test
    void threeDaysBackIsTheLastDayTheClassTeacherMayBackfill() {
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY.minusDays(3), TODAY)).isFalse();
    }

    @Test
    void fourDaysBackNeedsAnAdmin() {
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY.minusDays(4), TODAY)).isTrue();
        assertThat(AttendanceRules.backfillNeedsAdmin(TODAY.minusDays(30), TODAY)).isTrue();
    }

    @Test
    void futureDatesAreDetected() {
        assertThat(AttendanceRules.isFuture(TODAY, TODAY)).isFalse();
        assertThat(AttendanceRules.isFuture(TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(AttendanceRules.isFuture(TODAY.plusDays(1), TODAY)).isTrue();
    }
}
