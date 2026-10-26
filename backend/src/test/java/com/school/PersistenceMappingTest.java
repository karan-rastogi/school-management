package com.school;

import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceAuditLog;
import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceAuditLogRepository;
import com.school.attendance.repository.AttendanceRepository;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeScope;
import com.school.notice.repository.NoticeRepository;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.ParentStudentLinkId;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PersistenceMappingTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentEnrollmentRepository studentEnrollmentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private SchoolCalendarRepository schoolCalendarRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Test
    void everyMigrationIsAppliedAndHibernateValidatesTheResultingSchema() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);

        assertThat(applied).isEqualTo(6);
    }

    @Test
    void aUserCanHoldSeveralRolesAndLinkToSeveralStudents() {
        User parent = persistUser("Parent One", "9100000001");

        userRoleRepository.saveAndFlush(newUserRole(parent, Role.PARENT));
        userRoleRepository.saveAndFlush(newUserRole(parent, Role.TEACHER));

        Student first = persistStudent("ADM-1001");
        Student second = persistStudent("ADM-1002");

        parentStudentLinkRepository.saveAndFlush(link(parent, first, "FATHER"));
        parentStudentLinkRepository.saveAndFlush(link(parent, second, "GUARDIAN"));

        assertThat(userRoleRepository.findAll()).filteredOn(role -> parent.getId().equals(role.getUserId()))
                .hasSize(2);
        assertThat(parentStudentLinkRepository.findById(new ParentStudentLinkId(parent.getId(), first.getId())))
                .isPresent();
        assertThat(parentStudentLinkRepository.findById(new ParentStudentLinkId(parent.getId(), second.getId())))
                .isPresent();
    }

    @Test
    void aStudentReceivesARandomUniqueQrTokenFromTheDatabase() {
        Student first = persistStudent("ADM-2001");
        Student second = persistStudent("ADM-2002");

        assertThat(first.getQrToken()).isNotNull();
        assertThat(second.getQrToken()).isNotNull();
        assertThat(first.getQrToken()).isNotEqualTo(second.getQrToken());
    }

    @Test
    void classChangesAreKeptAsEnrollmentHistory() {
        AcademicYear year = persistAcademicYear("2025-2026", true);
        SchoolClass firstClass = persistClass("Grade 5", "A", year);
        SchoolClass secondClass = persistClass("Grade 5", "B", year);
        Student student = persistStudent("ADM-3001");

        studentEnrollmentRepository.saveAndFlush(enrollment(student, firstClass, 1,
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 9, 30)));
        studentEnrollmentRepository.saveAndFlush(enrollment(student, secondClass, 7,
                LocalDate.of(2025, 10, 1), null));

        assertThat(studentEnrollmentRepository.findAll())
                .filteredOn(found -> student.getId().equals(found.getStudent().getId()))
                .hasSize(2);
    }

    @Test
    void attendanceIsDailyAndUniquePerStudent() {
        User teacher = persistUser("Teacher One", "9100000002");
        Student student = persistStudent("ADM-4001");
        LocalDate date = LocalDate.of(2026, 1, 12);

        attendanceRepository.saveAndFlush(attendance(student, teacher, date, AttendanceStatus.PRESENT));
        attendanceRepository.flush();

        assertThatThrownBy(() -> attendanceRepository.saveAndFlush(
                attendance(student, teacher, date, AttendanceStatus.ABSENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attendanceAuditRowsRecordTheOldAndNewValue() {
        User teacher = persistUser("Teacher Two", "9100000003");
        Student student = persistStudent("ADM-5001");
        Attendance attendance = attendanceRepository.saveAndFlush(
                attendance(student, teacher, LocalDate.of(2026, 2, 3), AttendanceStatus.ABSENT));

        AttendanceAuditLog log = new AttendanceAuditLog();
        log.setAttendance(attendance);
        log.setChangedBy(teacher);
        log.setOldStatus(AttendanceStatus.ABSENT);
        log.setNewStatus(AttendanceStatus.PRESENT);
        log.setReason("Parent produced a medical certificate");

        AttendanceAuditLog saved = attendanceAuditLogRepository.saveAndFlush(log);

        AttendanceAuditLog reloaded = attendanceAuditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getOldStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(reloaded.getNewStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(reloaded.getChangedAt()).isNotNull();
        assertThat(reloaded.getAttendance().getId()).isEqualTo(attendance.getId());
    }

    @Test
    void aClassScopedNoticeMustReferenceAClass() {
        User teacher = persistUser("Teacher Three", "9100000004");

        Notice globalNotice = new Notice();
        globalNotice.setTitle("School closed");
        globalNotice.setBody("The school is closed tomorrow.");
        globalNotice.setScope(NoticeScope.GLOBAL);
        globalNotice.setSender(teacher);
        globalNotice.setSenderLabel("Principal");

        assertThat(noticeRepository.saveAndFlush(globalNotice).getId()).isNotNull();

        Notice brokenNotice = new Notice();
        brokenNotice.setTitle("Class notice");
        brokenNotice.setBody("Homework for section A.");
        brokenNotice.setScope(NoticeScope.CLASS);
        brokenNotice.setSender(teacher);
        brokenNotice.setSenderLabel("Maths Teacher");

        assertThatThrownBy(() -> noticeRepository.saveAndFlush(brokenNotice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theCalendarIsKeyedByItsSchoolLocalDate() {
        LocalDate date = LocalDate.of(2026, 1, 26);

        SchoolCalendarEntry entry = new SchoolCalendarEntry();
        entry.setDate(date);
        entry.setType(SchoolCalendarDayType.HOLIDAY);
        entry.setDescription("Republic Day");

        schoolCalendarRepository.saveAndFlush(entry);

        assertThat(schoolCalendarRepository.findById(date))
                .get()
                .extracting(SchoolCalendarEntry::getType)
                .isEqualTo(SchoolCalendarDayType.HOLIDAY);
    }

    private User persistUser(String name, String phone) {
        User user = new User();
        user.setName(name);
        user.setPhone(phone);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private UserRole newUserRole(User user, Role role) {
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRole(role);
        return userRole;
    }

    private Student persistStudent(String admissionNo) {
        Student student = new Student();
        student.setFullName("Student " + admissionNo);
        student.setAdmissionNo(admissionNo);
        student.setDob(LocalDate.of(2015, 6, 1));
        student.setAdmissionDate(LocalDate.of(2021, 4, 1));
        return studentRepository.saveAndFlush(student);
    }

    private AcademicYear persistAcademicYear(String name, boolean current) {
        AcademicYear academicYear = new AcademicYear();
        academicYear.setName(name);
        academicYear.setStartDate(LocalDate.of(2025, 4, 1));
        academicYear.setEndDate(LocalDate.of(2026, 3, 31));
        academicYear.setCurrent(current);
        return academicYearRepository.saveAndFlush(academicYear);
    }

    private SchoolClass persistClass(String name, String section, AcademicYear academicYear) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(name);
        schoolClass.setSection(section);
        schoolClass.setAcademicYear(academicYear);
        return schoolClassRepository.saveAndFlush(schoolClass);
    }

    private StudentEnrollment enrollment(Student student, SchoolClass schoolClass, Integer rollNo,
                                         LocalDate from, LocalDate to) {
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudent(student);
        enrollment.setSchoolClass(schoolClass);
        enrollment.setRollNo(rollNo);
        enrollment.setFromDate(from);
        enrollment.setToDate(to);
        return enrollment;
    }

    private Attendance attendance(Student student, User markedBy, LocalDate date, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setMarkedBy(markedBy);
        attendance.setDate(date);
        attendance.setStatus(status);
        attendance.setSource(AttendanceSource.MANUAL);
        return attendance;
    }

    private ParentStudentLink link(User parent, Student student, String relationship) {
        ParentStudentLink link = new ParentStudentLink();
        link.setParentUserId(parent.getId());
        link.setStudentId(student.getId());
        link.setRelationship(relationship);
        return link;
    }
}
