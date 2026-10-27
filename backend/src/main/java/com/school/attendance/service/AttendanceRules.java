package com.school.attendance.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class AttendanceRules {

    public static final int CLASS_TEACHER_BACKFILL_DAYS = 3;

    private AttendanceRules() {
    }

    public static boolean isFuture(LocalDate date, LocalDate today) {
        return date.isAfter(today);
    }

    public static boolean backfillNeedsAdmin(LocalDate date, LocalDate today) {
        return ChronoUnit.DAYS.between(date, today) > CLASS_TEACHER_BACKFILL_DAYS;
    }
}
