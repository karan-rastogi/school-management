package com.school.seed;

/**
 * Counts of the rows created by {@link SeedService}. Deliberately carries no credentials, so it is
 * safe to log.
 */
public record SeedCounts(
        int users,
        int userRoles,
        int academicYears,
        int schoolClasses,
        int students,
        int enrollments,
        int parentLinks,
        int teacherAssignments,
        int calendarEntries,
        int attendanceRecords,
        int attendanceUnmarked
) {
}
