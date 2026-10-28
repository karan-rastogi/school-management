package com.school.attendance.config;

import com.school.attendance.domain.AttendanceStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The attendance-percentage rules, defined once and configurable under {@code app.attendance}.
 *
 * <p>The percentage is a weighted sum of the days on which attendance was taken, divided by the
 * number of working days in the window on which attendance was taken. Concretely:
 *
 * <ul>
 *   <li>{@code PRESENT} counts as a full day.</li>
 *   <li>{@code LATE} counts as a full present day ({@code late-weight}, defaults to 1).</li>
 *   <li>{@code HALF_DAY} counts as half a day.</li>
 *   <li>{@code ABSENT} counts as nothing but still occupies a slot in the denominator.</li>
 *   <li>{@code EXCUSED} is an authorised absence: it is removed from the denominator entirely so it
 *       neither helps nor hurts the percentage.</li>
 * </ul>
 *
 * <p>Days on which attendance was never taken, and non-working days (holidays/weekends), are
 * excluded from both the numerator and the denominator.
 */
@ConfigurationProperties(prefix = "app.attendance")
public record AttendancePolicy(
        @DefaultValue("1.0") double presentWeight,
        @DefaultValue("1.0") double lateWeight,
        @DefaultValue("0.5") double halfDayWeight,
        @DefaultValue("0.0") double absentWeight,
        @DefaultValue("true") boolean excusedExcludedFromDenominator,
        @DefaultValue("75.0") double warningThresholdPercent
) {

    /**
     * How much a single day with the given status contributes to the numerator. Only meaningful for
     * statuses that {@link #countsTowardDenominator(AttendanceStatus) count toward the denominator}.
     */
    public double weight(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> presentWeight;
            case LATE -> lateWeight;
            case HALF_DAY -> halfDayWeight;
            case ABSENT, EXCUSED -> absentWeight;
        };
    }

    /**
     * Whether a day with the given status occupies a slot in the denominator. {@code EXCUSED} is
     * excluded when {@code excusedExcludedFromDenominator} is on (the default).
     */
    public boolean countsTowardDenominator(AttendanceStatus status) {
        return !(excusedExcludedFromDenominator && status == AttendanceStatus.EXCUSED);
    }

    /**
     * Whether the status represents a day the student missed (fully or partially). LATE counts as
     * present, so it is not an absence; EXCUSED is authorised, so it is not an absence either.
     */
    public boolean isAbsence(AttendanceStatus status) {
        return status == AttendanceStatus.ABSENT || status == AttendanceStatus.HALF_DAY;
    }

    public boolean belowThreshold(double percentage, int countedDays) {
        return countedDays > 0 && percentage < warningThresholdPercent;
    }
}
