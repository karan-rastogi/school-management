package com.school.attendance.config;

import com.school.attendance.domain.AttendanceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendancePolicyTest {

    private final AttendancePolicy policy = new AttendancePolicy(1.0, 1.0, 0.5, 0.0, true, 75.0);

    @Test
    void lateCountsAsPresentAndHalfDayAsHalfADay() {
        assertThat(policy.weight(AttendanceStatus.PRESENT)).isEqualTo(1.0);
        assertThat(policy.weight(AttendanceStatus.LATE)).isEqualTo(1.0);
        assertThat(policy.weight(AttendanceStatus.HALF_DAY)).isEqualTo(0.5);
        assertThat(policy.weight(AttendanceStatus.ABSENT)).isEqualTo(0.0);
    }

    @Test
    void excusedIsExcludedFromTheDenominator() {
        assertThat(policy.countsTowardDenominator(AttendanceStatus.PRESENT)).isTrue();
        assertThat(policy.countsTowardDenominator(AttendanceStatus.LATE)).isTrue();
        assertThat(policy.countsTowardDenominator(AttendanceStatus.HALF_DAY)).isTrue();
        assertThat(policy.countsTowardDenominator(AttendanceStatus.ABSENT)).isTrue();
        assertThat(policy.countsTowardDenominator(AttendanceStatus.EXCUSED)).isFalse();
    }

    @Test
    void excusedCountsWhenTheExclusionIsSwitchedOff() {
        AttendancePolicy inclusive = new AttendancePolicy(1.0, 1.0, 0.5, 0.0, false, 75.0);
        assertThat(inclusive.countsTowardDenominator(AttendanceStatus.EXCUSED)).isTrue();
        assertThat(inclusive.weight(AttendanceStatus.EXCUSED)).isEqualTo(0.0);
    }

    @Test
    void absencesAreFullOrPartiallyMissedDays() {
        assertThat(policy.isAbsence(AttendanceStatus.ABSENT)).isTrue();
        assertThat(policy.isAbsence(AttendanceStatus.HALF_DAY)).isTrue();
        assertThat(policy.isAbsence(AttendanceStatus.LATE)).isFalse();
        assertThat(policy.isAbsence(AttendanceStatus.EXCUSED)).isFalse();
    }

    @Test
    void theWarningOnlyFiresOnceAtLeastOneDayHasBeenCounted() {
        assertThat(policy.belowThreshold(62.5, 4)).isTrue();
        assertThat(policy.belowThreshold(75.0, 4)).isFalse();
        assertThat(policy.belowThreshold(80.0, 4)).isFalse();
        assertThat(policy.belowThreshold(100.0, 0)).isFalse();
    }
}
