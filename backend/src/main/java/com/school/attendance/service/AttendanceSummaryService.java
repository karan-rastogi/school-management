package com.school.attendance.service;

import com.school.academic.domain.AcademicYear;
import com.school.academic.repository.AcademicYearRepository;
import com.school.attendance.config.AttendancePolicy;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceRepository;
import com.school.attendance.web.dto.ChildAbsence;
import com.school.attendance.web.dto.ChildAttendanceDay;
import com.school.attendance.web.dto.ChildAttendanceResponse;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.service.SchoolCalendarService;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes a student's attendance percentage and the data the parent dashboard needs to explain it.
 *
 * <p>The counted window runs from {@code max(admission_date, academic-year start)} to today. A day
 * is counted when it is a working day (holidays excluded), attendance was actually taken for the
 * student, and the status is not excluded from the denominator (see {@link AttendancePolicy}).
 */
@Service
public class AttendanceSummaryService {

    private static final double NO_DAYS_PERCENTAGE = 100.0;

    private final AttendanceRepository attendanceRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolCalendarService schoolCalendarService;
    private final AttendancePolicy policy;
    private final Clock clock;

    public AttendanceSummaryService(
            AttendanceRepository attendanceRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            AcademicYearRepository academicYearRepository,
            SchoolCalendarService schoolCalendarService,
            AttendancePolicy policy,
            Clock clock
    ) {
        this.attendanceRepository = attendanceRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.academicYearRepository = academicYearRepository;
        this.schoolCalendarService = schoolCalendarService;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChildAttendanceResponse summarise(Student student, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth targetMonth = month == null ? YearMonth.from(today) : month;
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();

        StudentEnrollment enrollment = currentEnrollment(student.getId());
        LocalDate from = latest(student.getAdmissionDate(), academicYearStart(enrollment));
        LocalDate to = today;
        boolean rangeActive = !from.isAfter(to);

        List<Attendance> records = rangeActive
                ? attendanceRepository.findByStudent_IdAndDateBetweenOrderByDateAsc(student.getId(), from, to)
                : List.of();
        Map<LocalDate, AttendanceStatus> byDate = records.stream()
                .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (first, second) -> first));

        // One query for every calendar override the percentage and the month grid could touch.
        LocalDate windowFrom = earliest(earliest(from, to), monthStart);
        LocalDate windowTo = latest(latest(from, to), monthEnd);
        Map<LocalDate, SchoolCalendarDayType> overrides = schoolCalendarService.overridesBetween(windowFrom, windowTo);

        Tally tally = tally(records, overrides);
        double percentage = tally.countedDays == 0
                ? NO_DAYS_PERCENTAGE
                : round1(tally.presentEquivalentDays / tally.countedDays * 100.0);

        return new ChildAttendanceResponse(
                student.getId(),
                student.getFullName(),
                student.getAdmissionNo(),
                student.getPhotoPath(),
                enrollment == null ? null : enrollment.getSchoolClass().getId(),
                enrollment == null ? null : enrollment.getSchoolClass().getName(),
                enrollment == null ? null : enrollment.getSchoolClass().getSection(),
                from,
                to,
                targetMonth,
                percentage,
                round1(tally.presentEquivalentDays),
                tally.countedDays,
                tally.presentDays,
                tally.absentDays,
                tally.lateDays,
                tally.halfDays,
                tally.excusedDays,
                policy.warningThresholdPercent(),
                policy.belowThreshold(percentage, tally.countedDays),
                monthDays(targetMonth, from, to, rangeActive, today, byDate, overrides),
                tally.absences);
    }

    private Tally tally(List<Attendance> records, Map<LocalDate, SchoolCalendarDayType> overrides) {
        Tally tally = new Tally();
        for (Attendance record : records) {
            AttendanceStatus status = record.getStatus();
            if (!SchoolCalendarService.isWorkingDay(record.getDate(), overrides)) {
                continue;
            }
            if (status == AttendanceStatus.EXCUSED) {
                tally.excusedDays++;
                if (!policy.countsTowardDenominator(status)) {
                    continue;
                }
            }
            tally.countedDays++;
            tally.presentEquivalentDays += policy.weight(status);
            switch (status) {
                case PRESENT -> tally.presentDays++;
                case ABSENT -> tally.absentDays++;
                case LATE -> tally.lateDays++;
                case HALF_DAY -> tally.halfDays++;
                case EXCUSED -> {
                }
            }
            if (policy.isAbsence(status)) {
                tally.absences.add(new ChildAbsence(record.getDate(), status));
            }
        }
        tally.absences.sort(Comparator.comparing(ChildAbsence::date).reversed());
        return tally;
    }

    private List<ChildAttendanceDay> monthDays(
            YearMonth month,
            LocalDate from,
            LocalDate to,
            boolean rangeActive,
            LocalDate today,
            Map<LocalDate, AttendanceStatus> byDate,
            Map<LocalDate, SchoolCalendarDayType> overrides
    ) {
        List<ChildAttendanceDay> days = new ArrayList<>();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            boolean working = SchoolCalendarService.isWorkingDay(date, overrides);
            AttendanceStatus status = byDate.get(date);
            boolean withinWindow = rangeActive && !date.isBefore(from) && !date.isAfter(to);
            boolean counted = withinWindow && working && status != null && policy.countsTowardDenominator(status);
            days.add(new ChildAttendanceDay(
                    date, working, overrides.get(date), status, counted, date.isAfter(today)));
        }
        return days;
    }

    private StudentEnrollment currentEnrollment(Long studentId) {
        return studentEnrollmentRepository.findOpenEnrollmentsFor(List.of(studentId)).stream()
                .findFirst()
                .orElse(null);
    }

    private LocalDate academicYearStart(StudentEnrollment enrollment) {
        if (enrollment != null) {
            return enrollment.getSchoolClass().getAcademicYear().getStartDate();
        }
        return academicYearRepository.findByCurrentIsTrue()
                .map(AcademicYear::getStartDate)
                .orElse(LocalDate.MIN);
    }

    private static LocalDate latest(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate earliest(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class Tally {
        private int countedDays;
        private double presentEquivalentDays;
        private int presentDays;
        private int absentDays;
        private int lateDays;
        private int halfDays;
        private int excusedDays;
        private final List<ChildAbsence> absences = new ArrayList<>();
    }
}
