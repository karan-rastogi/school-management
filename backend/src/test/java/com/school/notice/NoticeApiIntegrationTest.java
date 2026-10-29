package com.school.notice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.auth.security.JwtService;
import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeScope;
import com.school.notice.repository.NoticeRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.notices.storage-directory=target/notice-test-attachments")
class NoticeApiIntegrationTest extends AbstractPostgresTest {

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
    private NoticeRepository noticeRepository;

    private LocalDate today;
    private String adminToken;
    private String teacherAToken;
    private String teacherBToken;
    private String parentAToken;
    private String parentBToken;
    private Long classAId;
    private Long classBId;
    private Long childAId;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        noticeRepository.deleteAll();

        User admin = ensureUser("+919666000001", "notice-admin@school.test", "Notice Admin", Role.ADMIN);
        User teacherA = ensureUser("+919666000002", "notice-teacher-a@school.test", "Mrs Sharma", Role.TEACHER);
        User teacherB = ensureUser("+919666000003", "notice-teacher-b@school.test", "Mr Rao", Role.TEACHER);
        User parentA = ensureUser("+919666000004", "notice-parent-a@school.test", "Parent A", Role.PARENT);
        User parentB = ensureUser("+919666000005", "notice-parent-b@school.test", "Parent B", Role.PARENT);

        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);
        teacherAToken = jwtService.issueAccessToken(teacherA.getId(), Role.TEACHER);
        teacherBToken = jwtService.issueAccessToken(teacherB.getId(), Role.TEACHER);
        parentAToken = jwtService.issueAccessToken(parentA.getId(), Role.PARENT);
        parentBToken = jwtService.issueAccessToken(parentB.getId(), Role.PARENT);

        AcademicYear year = academicYearRepository.findByNameIgnoreCase("Notice Year").orElseGet(() -> {
            AcademicYear created = new AcademicYear();
            created.setName("Notice Year");
            created.setStartDate(LocalDate.of(2020, 1, 1));
            created.setEndDate(LocalDate.of(2030, 12, 31));
            return academicYearRepository.saveAndFlush(created);
        });

        SchoolClass classA = persistClass("Notice Class", "A", year);
        SchoolClass classB = persistClass("Notice Class", "B", year);
        classAId = classA.getId();
        classBId = classB.getId();

        Student childA = persistStudent("ADM-NOTICE-A", "Aarav Notice");
        Student childB = persistStudent("ADM-NOTICE-B", "Bela Notice");
        childAId = childA.getId();

        enrolIfAbsent(childA, classA, 1);
        enrolIfAbsent(childB, classB, 1);

        link(parentA, childA);
        link(parentB, childB);

        assignTeacher(teacherA, classA, "Maths");
        assignTeacher(teacherB, classB, "Science");
    }

    @Test
    void anAdminPostsAGlobalNoticeAndTheParentSeesIt() throws Exception {
        mockMvc.perform(post("/notices").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "School closed", "body", "Closed on Friday.",
                                "scope", "GLOBAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.senderLabel").value("Notice Admin"))
                .andExpect(jsonPath("$.edited").value(false));

        mockMvc.perform(get("/parent/notices").cookie(parentACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("School closed"))
                .andExpect(jsonPath("$[0].childName").doesNotExist());
    }

    @Test
    void aTeacherPostsAClassNoticeWithALabelFromTheirAssignment() throws Exception {
        mockMvc.perform(post("/notices").cookie(teacherACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Homework", "body", "Chapter 4.", "scope", "CLASS",
                                "classId", classAId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("CLASS"))
                .andExpect(jsonPath("$.classId").value(classAId))
                .andExpect(jsonPath("$.senderLabel").value("Maths Teacher - Mrs Sharma"));

        mockMvc.perform(get("/parent/notices").cookie(parentACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderLabel").value("Maths Teacher - Mrs Sharma"))
                .andExpect(jsonPath("$[0].childName").value("Aarav Notice"));
    }

    @Test
    void aTeacherCannotPostToAClassTheyAreNotAssignedToOrPostAGlobalNotice() throws Exception {
        mockMvc.perform(post("/notices").cookie(teacherACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Nope", "body", "Body", "scope", "CLASS",
                                "classId", classBId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/notices").cookie(teacherACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Nope", "body", "Body", "scope", "GLOBAL"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void eachParentSeesGlobalAndOnlyTheirChildsClassNotices() throws Exception {
        postNotice(adminCookie(), Map.of("title", "Global news", "body", "Everyone.", "scope", "GLOBAL"));
        postNotice(teacherACookie(), Map.of("title", "Class A news", "body", "A only.", "scope", "CLASS",
                "classId", classAId));
        postNotice(teacherBCookie(), Map.of("title", "Class B news", "body", "B only.", "scope", "CLASS",
                "classId", classBId));

        List<String> titlesForA = titles(jsonArray(get("/parent/notices").cookie(parentACookie())));
        assertThat(titlesForA).containsExactlyInAnyOrder("Global news", "Class A news");
        assertThat(titlesForA).doesNotContain("Class B news");

        List<String> titlesForB = titles(jsonArray(get("/parent/notices").cookie(parentBCookie())));
        assertThat(titlesForB).containsExactlyInAnyOrder("Global news", "Class B news");
        assertThat(titlesForB).doesNotContain("Class A news");
    }

    @Test
    void editingShowsTheEditedBadge() throws Exception {
        long id = postNotice(teacherACookie(), Map.of("title", "Trip", "body", "Bring lunch.", "scope", "CLASS",
                "classId", classAId));

        mockMvc.perform(put("/notices/" + id).cookie(teacherACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Trip", "body", "Bring lunch and water."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.body").value("Bring lunch and water."))
                .andExpect(jsonPath("$.editedAt").exists());

        mockMvc.perform(get("/parent/notices").cookie(parentACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].edited").value(true));
    }

    @Test
    void aTeacherCannotEditAnotherTeachersNotice() throws Exception {
        long id = postNotice(teacherACookie(), Map.of("title", "Trip", "body", "Bring lunch.", "scope", "CLASS",
                "classId", classAId));

        mockMvc.perform(put("/notices/" + id).cookie(teacherBCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Hacked", "body", "Nope."))))
                .andExpect(status().isForbidden());
    }

    @Test
    void softDeleteRemovesTheNoticeFromEveryFeed() throws Exception {
        long id = postNotice(teacherACookie(), Map.of("title", "Old news", "body", "Body.", "scope", "CLASS",
                "classId", classAId));

        mockMvc.perform(delete("/notices/" + id).cookie(teacherACookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/parent/notices").cookie(parentACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/notices").cookie(teacherACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anExpiredNoticeIsHiddenFromParentsButKeptForStaff() throws Exception {
        Notice expired = new Notice();
        expired.setTitle("Past event");
        expired.setBody("Already over.");
        expired.setScope(NoticeScope.GLOBAL);
        expired.setSender(userRepository.findByPhone("+919666000001").orElseThrow());
        expired.setSenderLabel("Notice Admin");
        expired.setExpiresAt(OffsetDateTime.now(clock).minusDays(1));
        noticeRepository.saveAndFlush(expired);

        mockMvc.perform(get("/parent/notices").cookie(parentACookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/notices").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].expired").value(true));
    }

    @Test
    void theStaffListIsScopedToATeachersOwnClasses() throws Exception {
        postNotice(teacherACookie(), Map.of("title", "A notice", "body", "Body.", "scope", "CLASS", "classId", classAId));
        postNotice(teacherBCookie(), Map.of("title", "B notice", "body", "Body.", "scope", "CLASS", "classId", classBId));

        List<String> teacherTitles = titles(jsonArray(get("/notices").cookie(teacherACookie())).get("content"));
        assertThat(teacherTitles).containsExactly("A notice");

        List<String> adminTitles = titles(jsonArray(get("/notices").cookie(adminCookie())).get("content"));
        assertThat(adminTitles).containsExactlyInAnyOrder("A notice", "B notice");
    }

    @Test
    void anAttachmentCanBeUploadedAndFetchedThroughASignedUrl() throws Exception {
        long id = postNotice(adminCookie(), Map.of("title", "Circular", "body", "See attached.", "scope", "GLOBAL"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "circular.pdf", "application/pdf", "%PDF-CONTENT".getBytes(StandardCharsets.UTF_8));

        MvcResult uploaded = mockMvc.perform(multipart("/notices/" + id + "/attachment").file(file).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment.fileName").value("circular.pdf"))
                .andExpect(jsonPath("$.attachment.contentType").value("application/pdf"))
                .andReturn();

        String url = objectMapper.readTree(uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("attachment").get("url").asText();
        assertThat(url).startsWith("/notices/attachments/" + id + "?expires=");

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().bytes("%PDF-CONTENT".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(url + "tampered"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/notices/attachments/" + id + "?expires=1&signature=whatever"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCreatesAHolidayFromAGlobalNotice() throws Exception {
        long id = postNotice(adminCookie(), Map.of("title", "Diwali break", "body", "School closed.", "scope", "GLOBAL"));
        LocalDate holiday = today.plusDays(10);

        mockMvc.perform(post("/notices/" + id + "/holiday").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", holiday.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(holiday.toString()))
                .andExpect(jsonPath("$.type").value("HOLIDAY"))
                .andExpect(jsonPath("$.working").value(false));

        mockMvc.perform(get("/admin/calendar/" + holiday).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("HOLIDAY"))
                .andExpect(jsonPath("$.description").value("Diwali break"));
    }

    @Test
    void aTeacherCannotCreateAHolidayAndAClassNoticeCannotEither() throws Exception {
        long classNotice = postNotice(teacherACookie(), Map.of("title", "Class trip", "body", "Body.", "scope", "CLASS",
                "classId", classAId));

        mockMvc.perform(post("/notices/" + classNotice + "/holiday").cookie(teacherACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.plusDays(3).toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/notices/" + classNotice + "/holiday").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", today.plusDays(3).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theNoticeEndpointsEnforceAuthenticationAndRoles() throws Exception {
        mockMvc.perform(get("/notices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notices").cookie(parentACookie())).andExpect(status().isForbidden());
        mockMvc.perform(get("/parent/notices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/parent/notices").cookie(teacherACookie())).andExpect(status().isForbidden());
        mockMvc.perform(post("/notices").cookie(parentACookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "x", "body", "y", "scope", "GLOBAL"))))
                .andExpect(status().isForbidden());
    }

    private long postNotice(Cookie cookie, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/notices").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
    }

    private JsonNode jsonArray(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> titles(JsonNode array) {
        List<String> titles = new ArrayList<>();
        array.forEach(item -> titles.add(item.get("title").asText()));
        return titles;
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private Cookie teacherACookie() {
        return new Cookie(ACCESS_COOKIE, teacherAToken);
    }

    private Cookie teacherBCookie() {
        return new Cookie(ACCESS_COOKIE, teacherBToken);
    }

    private Cookie parentACookie() {
        return new Cookie(ACCESS_COOKIE, parentAToken);
    }

    private Cookie parentBCookie() {
        return new Cookie(ACCESS_COOKIE, parentBToken);
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

    private Student persistStudent(String admissionNo, String fullName) {
        return studentRepository.findByAdmissionNoIgnoreCase(admissionNo).orElseGet(() -> {
            Student student = new Student();
            student.setAdmissionNo(admissionNo);
            student.setFullName(fullName);
            student.setDob(LocalDate.of(2014, 4, 1));
            student.setAdmissionDate(LocalDate.of(2020, 6, 1));
            return studentRepository.saveAndFlush(student);
        });
    }

    private void enrolIfAbsent(Student student, SchoolClass schoolClass, Integer rollNo) {
        if (!studentEnrollmentRepository.findByStudentIdOrderByFromDateDesc(student.getId()).isEmpty()) {
            return;
        }
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudent(student);
        enrollment.setSchoolClass(schoolClass);
        enrollment.setRollNo(rollNo);
        enrollment.setFromDate(LocalDate.of(2020, 1, 1));
        studentEnrollmentRepository.saveAndFlush(enrollment);
    }

    private void link(User parent, Student student) {
        if (parentStudentLinkRepository.existsByParentUserIdAndStudentId(parent.getId(), student.getId())) {
            return;
        }
        ParentStudentLink link = new ParentStudentLink();
        link.setParentUserId(parent.getId());
        link.setStudentId(student.getId());
        link.setRelationship("MOTHER");
        parentStudentLinkRepository.saveAndFlush(link);
    }

    private void assignTeacher(User teacher, SchoolClass schoolClass, String subject) {
        if (teacherAssignmentRepository.existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCase(
                teacher.getId(), schoolClass.getId(), subject)) {
            return;
        }
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacher(teacher);
        assignment.setSchoolClass(schoolClass);
        assignment.setSubjectName(subject);
        assignment.setClassTeacher(false);
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
