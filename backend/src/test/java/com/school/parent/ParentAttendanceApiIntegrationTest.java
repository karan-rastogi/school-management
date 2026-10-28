package com.school.parent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceQueryRepository;
import com.school.attendance.repository.AttendanceRepository;
import com.school.auth.security.JwtService;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.TeacherAssignment;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ParentAttendanceApiIntegrationTest extends AbstractPostgresTest {

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
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;

    @Autowired
    private SchoolCalendarRepository schoolCalendarRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceQueryRepository attendanceQueryRepository;

    private LocalDate today;
    private User marker;
    private String parentToken;
    private String strangerParentToken;
    private String classTeacherToken;
    private String otherTeacherToken;
    private String adminToken;
    private Long childId;
    private Long classId;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);

        User parent = ensureUser("+919555000001", "parent-att@school.test", "Parent Att", Role.PARENT);
        User stranger = ensureUser("+919555000002", "parent-stranger@school.test", "Stranger Parent", Role.PARENT);
        User classTeacher = ensureUser("+919555000003", "parent-att-teacher@school.test", "Parent Att Teacher", Role.TEACHER);
        User otherTeacher = ensureUser("+919555000004", "parent-att-other@school.test", "Parent Att Other", Role.TEACHER);
        User admin = ensureUser("+919555000005", "parent-att-admin@school.test", "Parent Att Admin", Role.ADMIN);

        parentToken = jwtService.issueAccessToken(parent.getId(), Role.PARENT);
        strangerParentToken = jwtService.issueAccessToken(stranger.getId(), Role.PARENT);
        classTeacherToken = jwtService.issueAccessToken(classTeacher.getId(), Role.TEACHER);
        otherTeacherToken = jwtService.issueAccessToken(otherTeacher.getId(), Role.TEACHER);
        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);

        AcademicYear year = academicYearRepository.findByNameIgnoreCase("Parent Att Year").orElseGet(() -> {
            AcademicYear created = new AcademicYear();
            created.setName("Parent Att Year");
            created.setStartDate(LocalDate.of(2020, 1, 1));
            created.setEndDate(LocalDate.of(2030, 12, 31));
            return academicYearRepository.saveAndFlush(created);
        });

        SchoolClass schoolClass = schoolClassRepository
                .findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId("Parent Att Class", "A", year.getId())
                .orElseGet(() -> {
                    SchoolClass created = new SchoolClass();
                    created.setName("Parent Att Class");
                    created.setSection("A");
                    created.setAcademicYear(year);
                    return schoolClassRepository.saveAndFlush(created);
                });
        classId = schoolClass.getId();

        Student child = studentRepository.findByAdmissionNoIgnoreCase("ADM-PARENT-1").orElseGet(() -> {
            Student created = new Student();
            created.setAdmissionNo("ADM-PARENT-1");
            created.setFullName("Chotu Parent");
            created.setDob(LocalDate.of(2014, 5, 5));
            created.setAdmissionDate(today.minusDays(20));
            return studentRepository.saveAndFlush(created);
        });
        childId = child.getId();

        if (studentEnrollmentRepository.findByStudentIdOrderByFromDateDesc(childId).isEmpty()) {
            StudentEnrollment enrollment = new StudentEnrollment();
            enrollment.setStudent(child);
            enrollment.setSchoolClass(schoolClass);
            enrollment.setRollNo(1);
            enrollment.setFromDate(today.minusDays(20));
            studentEnrollmentRepository.saveAndFlush(enrollment);
        }

        if (!parentStudentLinkRepository.existsByParentUserIdAndStudentId(parent.getId(), childId)) {
            ParentStudentLink link = new ParentStudentLink();
            link.setParentUserId(parent.getId());
            link.setStudentId(childId);
            link.setRelationship("MOTHER");
            parentStudentLinkRepository.saveAndFlush(link);
        }

        ensureClassTeacher(classTeacher, schoolClass);

        marker = parent;
        seedAttendance(child);
    }

    @Test
    void thePercentageWeightsLateHalfDayAndExcludesExcusedAndHolidays() throws Exception {
        mockMvc.perform(get("/parent/children/" + childId + "/attendance").cookie(parentCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(childId))
                .andExpect(jsonPath("$.fullName").value("Chotu Parent"))
                .andExpect(jsonPath("$.classId").value(classId))
                // present(1) + late(1) + half(0.5) + absent(0) = 2.5 over 4 counted days
                .andExpect(jsonPath("$.percentage").value(62.5))
                .andExpect(jsonPath("$.presentEquivalentDays").value(2.5))
                .andExpect(jsonPath("$.countedDays").value(4))
                .andExpect(jsonPath("$.presentDays").value(1))
                .andExpect(jsonPath("$.lateDays").value(1))
                .andExpect(jsonPath("$.halfDays").value(1))
                .andExpect(jsonPath("$.absentDays").value(1))
                .andExpect(jsonPath("$.excusedDays").value(1))
                .andExpect(jsonPath("$.warningThresholdPercent").value(75.0))
                .andExpect(jsonPath("$.belowThreshold").value(true))
                .andExpect(jsonPath("$.absences.length()").value(2))
                .andExpect(jsonPath("$.absences[0].status").value("ABSENT"))
                .andExpect(jsonPath("$.absences[1].status").value("HALF_DAY"));
    }

    @Test
    void theMonthlyCalendarColoursDaysAndFlagsWhatIsCounted() throws Exception {
        JsonNode present = dayNode(today.minusDays(4));
        assertThat(present.get("status").asText()).isEqualTo("PRESENT");
        assertThat(present.get("counted").asBoolean()).isTrue();
        assertThat(present.get("working").asBoolean()).isTrue();

        JsonNode excused = dayNode(today);
        assertThat(excused.get("status").asText()).isEqualTo("EXCUSED");
        assertThat(excused.get("counted").asBoolean()).isFalse();

        JsonNode holiday = dayNode(today.minusDays(6));
        assertThat(holiday.get("calendarType").asText()).isEqualTo("HOLIDAY");
        assertThat(holiday.get("working").asBoolean()).isFalse();
        assertThat(holiday.get("counted").asBoolean()).isFalse();

        // A working day with no record: the status key is omitted (non-null JSON) and it is not counted.
        JsonNode unmarked = dayNode(today.minusDays(7));
        assertThat(unmarked.path("status").isMissingNode()).isTrue();
        assertThat(unmarked.get("counted").asBoolean()).isFalse();
    }

    /** Fetches the calendar for the month containing {@code date} and returns that day's cell. */
    private JsonNode dayNode(LocalDate date) throws Exception {
        JsonNode body = body(get("/parent/children/" + childId + "/attendance")
                .param("month", YearMonth.from(date).toString())
                .cookie(parentCookie()));
        JsonNode days = body.get("days");
        assertThat(days.isArray()).isTrue();
        return dayOn(days, date);
    }

    @Test
    void aParentCannotSeeAnotherParentsChild() throws Exception {
        mockMvc.perform(get("/parent/children/" + childId + "/attendance").cookie(strangerParentCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/parent/children/" + childId + "/attendance-queries").cookie(strangerParentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.minusDays(1).toString(), "message", "Please check"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void attendanceEndpointsAreParentOnlyAndRequireSignIn() throws Exception {
        mockMvc.perform(get("/parent/children"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/parent/children").cookie(classTeacherCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theParentSeesTheirLinkedChildren() throws Exception {
        mockMvc.perform(get("/parent/children").cookie(parentCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(childId))
                .andExpect(jsonPath("$[0].fullName").value("Chotu Parent"))
                .andExpect(jsonPath("$[0].relationship").value("MOTHER"))
                .andExpect(jsonPath("$[0].className").value("Parent Att Class"));
    }

    @Test
    void aParentRaisesAQueryOnceThenDuplicatesAreRejected() throws Exception {
        String body = json(Map.of("date", today.minusDays(1).toString(), "message", "Shreya was present, please check"));

        mockMvc.perform(post("/parent/children/" + childId + "/attendance-queries").cookie(parentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.message").value("Shreya was present, please check"))
                .andExpect(jsonPath("$.attendanceStatus").value("ABSENT"))
                .andExpect(jsonPath("$.studentId").value(childId));

        mockMvc.perform(post("/parent/children/" + childId + "/attendance-queries").cookie(parentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void raisingAQueryForAFutureDateIsRejected() throws Exception {
        mockMvc.perform(post("/parent/children/" + childId + "/attendance-queries").cookie(parentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.plusDays(1).toString(), "message", "Tomorrow?"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theClassTeacherSeesAndResolvesTheQueryButOtherTeachersCannot() throws Exception {
        raiseQuery();

        mockMvc.perform(get("/attendance/queries?status=OPEN").cookie(otherTeacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult listed = mockMvc.perform(get("/attendance/queries?status=OPEN").cookie(classTeacherCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(childId))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andReturn();
        long queryId = objectMapper.readTree(listed.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get(0).get("id").asLong();

        mockMvc.perform(post("/attendance/queries/" + queryId + "/resolve").cookie(otherTeacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("response", "Not my class"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/attendance/queries/" + queryId + "/resolve").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("response", "Corrected to present"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.response").value("Corrected to present"))
                .andExpect(jsonPath("$.respondedByName").value("Parent Att Teacher"));

        mockMvc.perform(post("/attendance/queries/" + queryId + "/resolve").cookie(classTeacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("response", "Again"))))
                .andExpect(status().isConflict());
    }

    @Test
    void queryEndpointsAreForTeachersAndAdminsOnly() throws Exception {
        mockMvc.perform(get("/attendance/queries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/attendance/queries").cookie(parentCookie())).andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/queries").cookie(adminToken())).andExpect(status().isOk());
    }

    private void seedAttendance(Student child) {
        LocalDate from = today.minusDays(10);
        attendanceRepository.deleteAll(
                attendanceRepository.findByStudent_IdAndDateBetweenOrderByDateAsc(child.getId(), from, today));
        attendanceQueryRepository.deleteAll(
                attendanceQueryRepository.findByStudentIdOrderByCreatedAtDesc(child.getId()));

        markDay(today.minusDays(4), SchoolCalendarDayType.WORKING, AttendanceStatus.PRESENT);
        markDay(today.minusDays(3), SchoolCalendarDayType.WORKING, AttendanceStatus.LATE);
        markDay(today.minusDays(2), SchoolCalendarDayType.WORKING, AttendanceStatus.HALF_DAY);
        markDay(today.minusDays(1), SchoolCalendarDayType.WORKING, AttendanceStatus.ABSENT);
        markDay(today, SchoolCalendarDayType.WORKING, AttendanceStatus.EXCUSED);

        // A holiday with a stray record and a working day never taken: both must be ignored.
        markDay(today.minusDays(6), SchoolCalendarDayType.HOLIDAY, AttendanceStatus.PRESENT);
        markDay(today.minusDays(7), SchoolCalendarDayType.WORKING, null);
    }

    private void markDay(LocalDate date, SchoolCalendarDayType type, AttendanceStatus status) {
        SchoolCalendarEntry entry = schoolCalendarRepository.findById(date).orElseGet(() -> {
            SchoolCalendarEntry created = new SchoolCalendarEntry();
            created.setDate(date);
            return created;
        });
        entry.setType(type);
        schoolCalendarRepository.saveAndFlush(entry);

        if (status == null) {
            return;
        }
        attendanceRepository.findByStudent_IdAndDate(childId, date).ifPresentOrElse(existing -> {
            existing.setStatus(status);
            attendanceRepository.saveAndFlush(existing);
        }, () -> {
            Attendance attendance = new Attendance();
            attendance.setStudent(studentRepository.getReferenceById(childId));
            attendance.setDate(date);
            attendance.setStatus(status);
            attendance.setSource(AttendanceSource.MANUAL);
            attendance.setMarkedBy(marker);
            attendanceRepository.saveAndFlush(attendance);
        });
    }

    private void raiseQuery() throws Exception {
        mockMvc.perform(post("/parent/children/" + childId + "/attendance-queries").cookie(parentCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.minusDays(1).toString(), "message", "Was present"))))
                .andExpect(status().isCreated());
    }

    private JsonNode dayOn(JsonNode days, LocalDate date) {
        for (JsonNode day : days) {
            if (day.get("date").asText().equals(date.toString())) {
                return day;
            }
        }
        throw new AssertionError("No calendar day for " + date);
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Cookie parentCookie() {
        return new Cookie(ACCESS_COOKIE, parentToken);
    }

    private Cookie strangerParentCookie() {
        return new Cookie(ACCESS_COOKIE, strangerParentToken);
    }

    private Cookie classTeacherCookie() {
        return new Cookie(ACCESS_COOKIE, classTeacherToken);
    }

    private Cookie otherTeacherToken() {
        return new Cookie(ACCESS_COOKIE, otherTeacherToken);
    }

    private Cookie adminToken() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private void ensureClassTeacher(User teacher, SchoolClass schoolClass) {
        if (teacherAssignmentRepository.existsActiveClassTeacher(teacher.getId(), schoolClass.getId())) {
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
