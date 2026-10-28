package com.school.attendance.service;

import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.attendance.config.AttendancePolicy;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceRepository;
import com.school.attendance.web.dto.ChildAttendanceDay;
import com.school.attendance.web.dto.ChildAttendanceResponse;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.calendar.service.SchoolCalendarService;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.domain.StudentStatus;
import com.school.student.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceSummaryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final StudentEnrollmentRepository studentEnrollmentRepository = mock(StudentEnrollmentRepository.class);
    private final AcademicYearRepository academicYearRepository = mock(AcademicYearRepository.class);
    private final SchoolCalendarRepository schoolCalendarRepository = mock(SchoolCalendarRepository.class);

    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
    private final SchoolCalendarService schoolCalendarService = new SchoolCalendarService(schoolCalendarRepository);

    @Test
    void weightsLateAsPresentHalfDayAsHalfAndDropsExcusedAndHolidays() {
        Student student = student();
        stubEnrollment(student);

        List<SchoolCalendarEntry> calendar = List.of(
                working(TODAY.minusDays(4)),
                working(TODAY.minusDays(3)),
                working(TODAY.minusDays(2)),
                working(TODAY.minusDays(1)),
                working(TODAY),
                working(TODAY.minusDays(7)),
                holiday(TODAY.minusDays(6)));
        List<Attendance> records = List.of(
                attendance(student, TODAY.minusDays(4), AttendanceStatus.PRESENT),
                attendance(student, TODAY.minusDays(3), AttendanceStatus.LATE),
                attendance(student, TODAY.minusDays(2), AttendanceStatus.HALF_DAY),
                attendance(student, TODAY.minusDays(1), AttendanceStatus.ABSENT),
                attendance(student, TODAY, AttendanceStatus.EXCUSED),
                // A stray record on a holiday must be ignored even though attendance was "taken".
                attendance(student, TODAY.minusDays(6), AttendanceStatus.PRESENT));

        stubCalendar(calendar);
        when(attendanceRepository.findByStudent_IdAndDateBetweenOrderByDateAsc(eq(student.getId()), any(), any()))
                .thenReturn(records);

        ChildAttendanceResponse response = service(new AttendancePolicy(1.0, 1.0, 0.5, 0.0, true, 75.0))
                .summarise(student, YearMonth.of(2026, 10));

        assertThat(response.fromDate()).isEqualTo(TODAY.minusDays(20));
        assertThat(response.toDate()).isEqualTo(TODAY);
        assertThat(response.countedDays()).isEqualTo(4);
        assertThat(response.presentEquivalentDays()).isEqualTo(2.5);
        assertThat(response.percentage()).isEqualTo(62.5);
        assertThat(response.presentDays()).isEqualTo(1);
        assertThat(response.lateDays()).isEqualTo(1);
        assertThat(response.halfDays()).isEqualTo(1);
        assertThat(response.absentDays()).isEqualTo(1);
        assertThat(response.excusedDays()).isEqualTo(1);
        assertThat(response.belowThreshold()).isTrue();
        assertThat(response.warningThresholdPercent()).isEqualTo(75.0);
        assertThat(response.absences())
                .extracting(absence -> absence.date(), absence -> absence.status())
                .containsExactly(
                        tuple(TODAY.minusDays(1), AttendanceStatus.ABSENT),
                        tuple(TODAY.minusDays(2), AttendanceStatus.HALF_DAY));

        ChildAttendanceDay excusedDay = day(response, TODAY);
        assertThat(excusedDay.status()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(excusedDay.counted()).isFalse();

        ChildAttendanceDay presentDay = day(response, TODAY.minusDays(4));
        assertThat(presentDay.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(presentDay.counted()).isTrue();

        ChildAttendanceDay holidayDay = day(response, TODAY.minusDays(6));
        assertThat(holidayDay.calendarType()).isEqualTo(SchoolCalendarDayType.HOLIDAY);
        assertThat(holidayDay.working()).isFalse();
        assertThat(holidayDay.counted()).isFalse();

        ChildAttendanceDay unmarkedDay = day(response, TODAY.minusDays(7));
        assertThat(unmarkedDay.status()).isNull();
        assertThat(unmarkedDay.counted()).isFalse();
    }

    @Test
    void excusedCountsAgainstTheStudentWhenTheExclusionIsSwitchedOff() {
        Student student = student();
        stubEnrollment(student);
        stubCalendar(List.of(working(TODAY.minusDays(4)), working(TODAY)));
        when(attendanceRepository.findByStudent_IdAndDateBetweenOrderByDateAsc(eq(student.getId()), any(), any()))
                .thenReturn(List.of(
                        attendance(student, TODAY.minusDays(4), AttendanceStatus.PRESENT),
                        attendance(student, TODAY, AttendanceStatus.EXCUSED)));

        ChildAttendanceResponse response = service(new AttendancePolicy(1.0, 1.0, 0.5, 0.0, false, 75.0))
                .summarise(student, YearMonth.of(2026, 10));

        assertThat(response.countedDays()).isEqualTo(2);
        assertThat(response.percentage()).isEqualTo(50.0);
    }

    @Test
    void aChildWithNoAttendanceYetIsNotFlaggedAsBelowThreshold() {
        Student student = student();
        stubEnrollment(student);
        stubCalendar(List.of());
        when(attendanceRepository.findByStudent_IdAndDateBetweenOrderByDateAsc(eq(student.getId()), any(), any()))
                .thenReturn(List.of());

        ChildAttendanceResponse response = service(new AttendancePolicy(1.0, 1.0, 0.5, 0.0, true, 75.0))
                .summarise(student, YearMonth.of(2026, 10));

        assertThat(response.countedDays()).isZero();
        assertThat(response.percentage()).isEqualTo(100.0);
        assertThat(response.belowThreshold()).isFalse();
    }

    @Test
    void anAdmissionInTheFutureLeavesNothingToCount() {
        Student student = student();
        student.setAdmissionDate(TODAY.plusDays(5));
        stubEnrollment(student);
        stubCalendar(List.of());

        ChildAttendanceResponse response = service(new AttendancePolicy(1.0, 1.0, 0.5, 0.0, true, 75.0))
                .summarise(student, YearMonth.of(2026, 10));

        assertThat(response.countedDays()).isZero();
        assertThat(response.percentage()).isEqualTo(100.0);
    }

    private AttendanceSummaryService service(AttendancePolicy policy) {
        return new AttendanceSummaryService(
                attendanceRepository,
                studentEnrollmentRepository,
                academicYearRepository,
                schoolCalendarService,
                policy,
                clock);
    }

    private void stubEnrollment(Student student) {
        when(studentEnrollmentRepository.findOpenEnrollmentsFor(any())).thenReturn(List.of(enrollment(student)));
    }

    private void stubCalendar(List<SchoolCalendarEntry> calendar) {
        when(schoolCalendarRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(new ArrayList<>(calendar));
    }

    private static ChildAttendanceDay day(ChildAttendanceResponse response, LocalDate date) {
        return response.days().stream()
                .filter(day -> day.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No calendar day for " + date));
    }

    private static Student student() {
        Student student = new Student();
        student.setId(7L);
        student.setFullName("Chotu Parent");
        student.setAdmissionNo("ADM-1");
        student.setDob(LocalDate.of(2014, 5, 5));
        student.setAdmissionDate(TODAY.minusDays(20));
        student.setStatus(StudentStatus.ACTIVE);
        return student;
    }

    private static StudentEnrollment enrollment(Student student) {
        AcademicYear year = new AcademicYear();
        year.setId(1L);
        year.setName("2026-2027");
        year.setStartDate(LocalDate.of(2020, 1, 1));
        year.setEndDate(LocalDate.of(2030, 12, 31));

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(3L);
        schoolClass.setName("Grade 5");
        schoolClass.setSection("A");
        schoolClass.setAcademicYear(year);

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudent(student);
        enrollment.setSchoolClass(schoolClass);
        enrollment.setFromDate(LocalDate.of(2020, 1, 1));
        return enrollment;
    }

    private static Attendance attendance(Student student, LocalDate date, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setDate(date);
        attendance.setStatus(status);
        attendance.setSource(AttendanceSource.MANUAL);
        return attendance;
    }

    private static SchoolCalendarEntry working(LocalDate date) {
        return entry(date, SchoolCalendarDayType.WORKING);
    }

    private static SchoolCalendarEntry holiday(LocalDate date) {
        return entry(date, SchoolCalendarDayType.HOLIDAY);
    }

    private static SchoolCalendarEntry entry(LocalDate date, SchoolCalendarDayType type) {
        SchoolCalendarEntry entry = new SchoolCalendarEntry();
        entry.setDate(date);
        entry.setType(type);
        return entry;
    }
}
