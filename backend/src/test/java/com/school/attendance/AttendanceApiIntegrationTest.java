package com.school.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceAuditLog;
import com.school.attendance.repository.AttendanceAuditLogRepository;
import com.school.attendance.repository.AttendanceRepository;
import com.school.auth.security.JwtService;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.SubstitutePermission;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.SubstitutePermissionRepository;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AttendanceApiIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Clock clock;

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
    private TeacherAssignmentRepository teacherAssignmentRepository;

    @Autowired
    private SubstitutePermissionRepository substitutePermissionRepository;

    @Autowired
    private SchoolCalendarRepository schoolCalendarRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    private LocalDate today;
    private String adminToken;
    private String classTeacherToken;
    private String otherTeacherToken;
    private String parentToken;
    private Long classAId;
    private Long clockClassId;
    private Long studentA1Id;
    private Long studentA2Id;
    private UUID studentA1Qr;
    private UUID studentB1Qr;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);

        User admin = ensureUser("+919444000001", "att-admin@school.test", "Attendance Admin", Role.ADMIN);
        User classTeacher = ensureUser("+919444000002", "att-teacher@school.test", "Class Teacher", Role.TEACHER);
        User otherTeacher = ensureUser("+919444000003", "att-other@school.test", "Other Teacher", Role.TEACHER);
        User parent = ensureUser("+919444000004", "att-parent@school.test", "Attendance Parent", Role.PARENT);

        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);
        classTeacherToken = jwtService.issueAccessToken(classTeacher.getId(), Role.TEACHER);
        otherTeacherToken = jwtService.issueAccessToken(otherTeacher.getId(), Role.TEACHER);
        parentToken = jwtService.issueAccessToken(parent.getId(), Role.PARENT);

        AcademicYear year = academicYearRepository.findByNameIgnoreCase("Attendance Year").orElseGet(() -> {
            AcademicYear created = new AcademicYear();
            created.setName("Attendance Year");
            created.setStartDate(LocalDate.of(2020, 1, 1));
            created.setEndDate(LocalDate.of(2030, 12, 31));
            return academicYearRepository.saveAndFlush(created);
        });

        substitutePermissionRepository.deleteAll();

        SchoolClass classA = persistClass("Att Class", "A", year);
        SchoolClass classB = persistClass("Att Class", "B", year);
        classAId = classA.getId();

        Student studentA1 = persistStudent("ADM-ATT-A1", "Aarav Att", "/photos/aarav.png");
        Student studentA2 = persistStudent("ADM-ATT-A2", "Diya Att", null);
        Student studentB1 = persistStudent("ADM-ATT-B1", "Bela Att", null);
        studentA1Id = studentA1.getId();
        studentA2Id = studentA2.getId();
        studentA1Qr = studentA1.getQrToken();
        studentB1Qr = studentB1.getQrToken();

        enrol(studentA1, classA, 1);
        enrol(studentA2, classA, 2);
        enrol(studentB1, classB, 1);

        ensureClassTeacher(classTeacher, classA);

        attendanceRepository.deleteAll(attendanceRepository.findAll().stream()
                .filter(row -> row.getDate().equals(today) || row.getDate().isAfter(today.minusDays(10)))
                .toList());
    }

    @Test
    void attendanceEndpointsRequireATeacherOrAdmin() throws Exception {
        mockMvc.perform(get("/classes/" + classAId + "/attendance?date=" + today))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/classes/" + classAId + "/attendance?date=" + today).cookie(parentCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/attendance/scan").cookie(parentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", studentA1Qr.toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeacherWhoIsNotAssignedToTheClassCannotReadOrMarkIt() throws Exception {
        mockMvc.perform(get("/classes/" + classAId + "/attendance?date=" + today).cookie(otherCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/attendance/scan").cookie(otherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", studentA1Qr.toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theClassTeacherCanScanAndScanningTwiceIsIdempotent() throws Exception {
        markDay(today, SchoolCalendarDayType.WORKING);

        mockMvc.perform(post("/attendance/scan").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", studentA1Qr.toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentA1Id))
                .andExpect(jsonPath("$.fullName").value("Aarav Att"))
                .andExpect(jsonPath("$.photoPath").value("/photos/aarav.png"))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.marked").value(true))
                .andExpect(jsonPath("$.alreadyMarked").value(false));

        mockMvc.perform(post("/attendance/scan").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", studentA1Qr.toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(false))
                .andExpect(jsonPath("$.alreadyMarked").value(true))
                .andExpect(jsonPath("$.message").value(containsString("Already marked at")));

        List<Attendance> rows = attendanceRepository.findByDateAndStudent_IdIn(today, List.of(studentA1Id));
        assertThat(rows).hasSize(1);
        assertThat(auditCount(rows.get(0).getId())).isEqualTo(1);
    }

    @Test
    void scanningAStudentFromAnotherClassReturnsAWarningAndDoesNotMark() throws Exception {
        markDay(today, SchoolCalendarDayType.WORKING);

        mockMvc.perform(post("/attendance/scan").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", studentB1Qr.toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(false))
                .andExpect(jsonPath("$.alreadyMarked").value(false))
                .andExpect(jsonPath("$.warning").value(containsString("is not enrolled")));

        assertThat(attendanceRepository.findByDateAndStudent_IdIn(today, List.of(studentB1QrStudent()))).isEmpty();
    }

    @Test
    void anUnknownQrTokenIsReportedAsNotFound() throws Exception {
        markDay(today, SchoolCalendarDayType.WORKING);

        mockMvc.perform(post("/attendance/scan").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("qrToken", UUID.randomUUID().toString(), "classId", classAId, "date", today.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void markingOnAHolidayIsRejectedButAnAdminCanOverride() throws Exception {
        LocalDate holiday = today.minusDays(5);
        markDay(holiday, SchoolCalendarDayType.HOLIDAY);

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", holiday.toString(), "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", holiday.toString(), "overrideHoliday", true, "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.present").value(1));
    }

    @Test
    void paperBackfillUpToThreeDaysBackIsAllowedForTheClassTeacher() throws Exception {
        LocalDate date = today.minusDays(3);
        markDay(date, SchoolCalendarDayType.WORKING);

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", date.toString(), "source", "PAPER_BACKFILL", "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.present").value(1));
    }

    @Test
    void paperBackfillOlderThanThreeDaysNeedsAnAdmin() throws Exception {
        LocalDate date = today.minusDays(4);
        markDay(date, SchoolCalendarDayType.WORKING);

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", date.toString(), "source", "PAPER_BACKFILL", "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", date.toString(), "source", "PAPER_BACKFILL", "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isOk());
    }

    @Test
    void markingAFutureDateIsRejected() throws Exception {
        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.plusDays(1).toString(), "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theRosterSeparatesUnmarkedFromAbsentAndEveryEditIsAudited() throws Exception {
        markDay(today, SchoolCalendarDayType.WORKING);

        mockMvc.perform(get("/classes/" + classAId + "/attendance?date=" + today).cookie(classTeacherCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(2))
                .andExpect(jsonPath("$.counts.unmarked").value(2))
                .andExpect(jsonPath("$.counts.absent").value(0))
                .andExpect(jsonPath("$.workingDay").value(true));

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.toString(), "entries",
                                List.of(Map.of("studentId", studentA1Id, "status", "ABSENT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.absent").value(1))
                .andExpect(jsonPath("$.counts.unmarked").value(1));

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.toString(), "entries",
                                List.of(Map.of("studentId", studentA1Id, "status", "LATE"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.late").value(1))
                .andExpect(jsonPath("$.counts.absent").value(0));

        List<Attendance> rows = attendanceRepository.findByDateAndStudent_IdIn(today, List.of(studentA1Id));
        assertThat(rows).hasSize(1);
        assertThat(auditCount(rows.get(0).getId())).isEqualTo(2);
    }

    @Test
    void aSubstitutePermissionLetsAnotherTeacherMarkTheClass() throws Exception {
        markDay(today, SchoolCalendarDayType.WORKING);

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(otherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.toString(), "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isForbidden());

        Long otherTeacherId = userRepository.findByPhone("+919444000003").orElseThrow().getId();
        mockMvc.perform(post("/admin/classes/" + classAId + "/substitute-permissions").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherUserId", otherTeacherId,
                                "validFrom", today.minusDays(1).toString(),
                                "validUntil", today.plusDays(1).toString(),
                                "reason", "Class teacher on leave"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherName").value("Other Teacher"));

        mockMvc.perform(put("/classes/" + classAId + "/attendance").cookie(otherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.toString(), "entries",
                                List.of(Map.of("studentId", studentA2Id, "status", "PRESENT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.present").value(1));

        mockMvc.perform(get("/attendance/classes?date=" + today).cookie(otherCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classId").value(classAId))
                .andExpect(jsonPath("$[0].role").value("SUBSTITUTE"));
    }

    @Test
    void substitutePermissionEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/admin/classes/" + classAId + "/substitute-permissions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/classes/" + classAId + "/substitute-permissions").cookie(classTeacherCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theClassTeacherSeesTheirClassInTheMarkableList() throws Exception {
        mockMvc.perform(get("/attendance/classes?date=" + today).cookie(classTeacherCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classId").value(classAId))
                .andExpect(jsonPath("$[0].role").value("CLASS_TEACHER"));
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private Cookie classTeacherCookie() {
        return new Cookie(ACCESS_COOKIE, classTeacherToken);
    }

    private Cookie otherCookie() {
        return new Cookie(ACCESS_COOKIE, otherTeacherToken);
    }

    private Cookie parentCookie() {
        return new Cookie(ACCESS_COOKIE, parentToken);
    }

    private Long studentB1QrStudent() {
        return studentRepository.findByQrToken(studentB1Qr).orElseThrow().getId();
    }

    private long auditCount(Long attendanceId) {
        return attendanceAuditLogRepository.findAll().stream()
                .filter(log -> attendanceId.equals(log.getAttendance().getId()))
                .count();
    }

    private void markDay(LocalDate date, SchoolCalendarDayType type) {
        SchoolCalendarEntry entry = schoolCalendarRepository.findById(date).orElseGet(() -> {
            SchoolCalendarEntry created = new SchoolCalendarEntry();
            created.setDate(date);
            return created;
        });
        entry.setType(type);
        schoolCalendarRepository.saveAndFlush(entry);
    }

    private SchoolClass persistClass(String name, String section, AcademicYear year) {
        return schoolClassRepository
                .findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(name, section, year.getId())
                .orElseGet(() -> {
                    SchoolClass schoolClass = new SchoolClass();
                    schoolClass.setName(name);
                    schoolClass.setSection(section);
                    schoolClass.setAcademicYear(year);
                    return schoolClassRepository.saveAndFlush(schoolClass);
                });
    }

    private Student persistStudent(String admissionNo, String fullName, String photoPath) {
        return studentRepository.findByAdmissionNoIgnoreCase(admissionNo).orElseGet(() -> {
            Student student = new Student();
            student.setAdmissionNo(admissionNo);
            student.setFullName(fullName);
            student.setPhotoPath(photoPath);
            student.setDob(LocalDate.of(2014, 4, 1));
            student.setAdmissionDate(LocalDate.of(2020, 6, 1));
            return studentRepository.saveAndFlush(student);
        });
    }

    private void enrol(Student student, SchoolClass schoolClass, Integer rollNo) {
        LocalDate from = LocalDate.of(2020, 1, 1);
        if (studentEnrollmentRepository.existsByStudentIdAndFromDate(student.getId(), from)) {
            return;
        }
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudent(student);
        enrollment.setSchoolClass(schoolClass);
        enrollment.setRollNo(rollNo);
        enrollment.setFromDate(from);
        studentEnrollmentRepository.saveAndFlush(enrollment);
    }

    private void ensureClassTeacher(User teacher, SchoolClass schoolClass) {
        boolean exists = teacherAssignmentRepository.existsActiveClassTeacher(teacher.getId(), schoolClass.getId());
        if (exists) {
            return;
        }
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacher(teacher);
        assignment.setSchoolClass(schoolClass);
        assignment.setSubjectName("Class");
        assignment.setClassTeacher(true);
        assignment.setActive(true);
        teacherAssignmentRepository.saveAndFlush(assignment);
    }

    private User ensureUser(String phone, String email, String name, Role role) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setName(name);
            user.setActive(true);
            User saved = userRepository.saveAndFlush(user);
            UserRole userRole = new UserRole();
            userRole.setUserId(saved.getId());
            userRole.setRole(role);
            userRoleRepository.saveAndFlush(userRole);
            return saved;
        });
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
