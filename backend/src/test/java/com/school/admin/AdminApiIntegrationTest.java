package com.school.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.auth.security.JwtService;
import com.school.student.domain.Student;
import com.school.student.repository.StudentRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminApiIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StudentRepository studentRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void signInAsAdmin() {
        admin = ensureUser("+919111000001", "it-admin@school.test", "Integration Admin", Role.ADMIN);
        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(get("/admin/students")).andExpect(status().isUnauthorized());

        User teacher = ensureUser("+919111000002", "it-teacher@school.test", "Integration Teacher", Role.TEACHER);
        String teacherToken = jwtService.issueAccessToken(teacher.getId(), Role.TEACHER);

        mockMvc.perform(get("/admin/students").cookie(new Cookie(ACCESS_COOKIE, teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void academicYearsCanBeCreatedSearchedAndUpdated() throws Exception {
        long id = createAcademicYear("AY-Alpha 2030", "2030-04-01", "2031-03-31", false);

        mockMvc.perform(post("/admin/academic-years")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "AY-Alpha 2030",
                                "startDate", "2030-04-01",
                                "endDate", "2031-03-31",
                                "current", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        mockMvc.perform(post("/admin/academic-years")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "AY-Alpha 2032",
                                "startDate", "2032-04-01",
                                "endDate", "2031-03-31",
                                "current", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/admin/academic-years/" + id)
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "AY-Alpha 2030",
                                "startDate", "2030-04-01",
                                "endDate", "2031-03-31",
                                "current", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true));

        mockMvc.perform(get("/admin/academic-years?query=AY-Alpha&page=0&size=10").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("AY-Alpha 2030"));
    }

    @Test
    void onlyOneAcademicYearCanBeCurrent() throws Exception {
        long first = createAcademicYear("AY-Beta 2023", "2023-04-01", "2024-03-31", true);
        long second = createAcademicYear("AY-Beta 2022", "2022-04-01", "2023-03-31", true);

        mockMvc.perform(get("/admin/academic-years/" + first).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(false));

        mockMvc.perform(get("/admin/academic-years/" + second).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    void aClassCannotBeDeletedWhileStudentsAreEnrolled() throws Exception {
        long yearId = createAcademicYear("AY-Gamma 2021", "2021-04-01", "2022-03-31", false);
        long classId = createClass("Grade 7", "A", yearId);

        mockMvc.perform(delete("/admin/classes/" + classId).cookie(adminCookie()))
                .andExpect(status().isNoContent());

        long keptClassId = createClass("Grade 8", "A", yearId);
        long studentId = createStudent("ADM-CLS-1", "Class Child");

        mockMvc.perform(post("/admin/students/" + studentId + "/enrollments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classId", keptClassId, "rollNo", 4, "fromDate", "2021-06-01"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.className").value("Grade 8"))
                .andExpect(jsonPath("$.rollNo").value(4));

        mockMvc.perform(delete("/admin/classes/" + keptClassId).cookie(adminCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(delete("/admin/academic-years/" + yearId).cookie(adminCookie()))
                .andExpect(status().isConflict());
    }

    @Test
    void classesAreUniquePerNameSectionAndAcademicYear() throws Exception {
        long yearId = createAcademicYear("AY-Delta 2020", "2020-04-01", "2021-03-31", false);
        createClass("Grade 6", "B", yearId);

        mockMvc.perform(post("/admin/classes")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Grade 6", "section", "B", "academicYearId", yearId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        mockMvc.perform(get("/admin/classes/options?academicYearId=" + yearId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void teachersAreUsersWithTheTeacherRoleAndRolesCanBeReplaced() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/users")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Meera Teacher",
                                "phone", "+919111000010",
                                "email", "meera.teacher@school.test",
                                "roles", List.of("TEACHER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andReturn();

        long teacherId = readId(created);

        mockMvc.perform(get("/admin/users?role=TEACHER&query=Meera").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Meera Teacher"));

        mockMvc.perform(put("/admin/users/" + teacherId + "/roles")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("TEACHER", "PARENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        mockMvc.perform(post("/admin/users")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "No Contact",
                                "roles", List.of("TEACHER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/admin/users")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Duplicate Phone",
                                "phone", "+919111000010",
                                "roles", List.of("TEACHER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void teacherAssignmentsRejectDuplicateSubjectsAndASecondClassTeacher() throws Exception {
        long yearId = createAcademicYear("AY-Epsilon 2019", "2019-04-01", "2020-03-31", false);
        long classId = createClass("Grade 9", "A", yearId);
        long teacherId = createUser("+919111000020", "assign.teacher@school.test", "Assign Teacher", Role.TEACHER);
        long otherTeacherId = createUser("+919111000021", "assign.other@school.test", "Other Teacher", Role.TEACHER);

        mockMvc.perform(post("/admin/teacher-assignments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherUserId", teacherId,
                                "classId", classId,
                                "subjectName", "Maths",
                                "classTeacher", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.className").value("Grade 9"))
                .andExpect(jsonPath("$.classTeacher").value(true));

        mockMvc.perform(post("/admin/teacher-assignments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherUserId", teacherId,
                                "classId", classId,
                                "subjectName", "Maths",
                                "classTeacher", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        mockMvc.perform(post("/admin/teacher-assignments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherUserId", otherTeacherId,
                                "classId", classId,
                                "subjectName", "Science",
                                "classTeacher", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("class teacher")));

        mockMvc.perform(post("/admin/teacher-assignments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherUserId", admin.getId(),
                                "classId", classId,
                                "subjectName", "Art",
                                "classTeacher", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TEACHER role")));

        mockMvc.perform(get("/admin/teacher-assignments?classId=" + classId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void studentsCanBeSearchedPagedAndFilteredByClass() throws Exception {
        long yearId = createAcademicYear("AY-Zeta 2018", "2018-04-01", "2019-03-31", false);
        long classId = createClass("Grade 4", "Z", yearId);

        long first = createStudent("ADM-S-1", "Zeta Search One");
        createStudent("ADM-S-2", "Zeta Search Two");
        createStudent("ADM-S-3", "Zeta Search Three");

        mockMvc.perform(post("/admin/students/" + first + "/enrollments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classId", classId, "fromDate", "2018-06-01"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/students?query=Zeta Search").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/admin/students?query=Zeta Search&page=0&size=2").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/admin/students?classId=" + classId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currentClassName").value("Grade 4"))
                .andExpect(jsonPath("$.content[0].rollNo").doesNotExist());
    }

    @Test
    void aStudentDetailListsEnrollmentsAndParentsAndTheQrTokenCanBeRegenerated() throws Exception {
        long yearId = createAcademicYear("AY-Eta 2017", "2017-04-01", "2018-03-31", false);
        long classId = createClass("Grade 3", "A", yearId);
        long studentId = createStudent("ADM-D-1", "Detail Student");
        long parentId = createUser("+919111000030", "detail.parent@school.test", "Detail Parent", Role.PARENT);

        mockMvc.perform(post("/admin/students/" + studentId + "/enrollments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classId", classId, "rollNo", 2, "fromDate", "2017-06-01"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/parent-student-links")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "parentUserId", parentId,
                                "studentId", studentId,
                                "relationship", "MOTHER"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/parent-student-links")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("parentUserId", parentId, "studentId", studentId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        MvcResult detail = mockMvc.perform(get("/admin/students/" + studentId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments.length()").value(1))
                .andExpect(jsonPath("$.parents[0].relationship").value("MOTHER"))
                .andExpect(jsonPath("$.parents[0].parentName").value("Detail Parent"))
                .andReturn();

        String originalToken = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("qrToken").asText();

        MvcResult regenerated = mockMvc.perform(post("/admin/students/" + studentId + "/qr-token").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String newToken = objectMapper.readTree(regenerated.getResponse().getContentAsString())
                .get("qrToken").asText();

        assertThat(newToken).isNotBlank().isNotEqualTo(originalToken);

        Student reloaded = studentRepository.findById(studentId).orElseThrow();
        assertThat(reloaded.getQrToken().toString()).isEqualTo(newToken);

        mockMvc.perform(get("/admin/parents/" + parentId + "/students").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].admissionNo").value("ADM-D-1"));
    }

    @Test
    void aQrTokenIsOpaqueAndNeverCarriesPersonalData() throws Exception {
        long studentId = createStudent("ADM-QR-1", "Token Student");

        MvcResult detail = mockMvc.perform(get("/admin/students/" + studentId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("qrToken").asText();

        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(token).doesNotContain("Token", "ADM-QR-1");
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private long createAcademicYear(String name, String start, String end, boolean current) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/academic-years")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "startDate", start, "endDate", end, "current", current))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long createClass(String name, String section, long academicYearId) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/classes")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "section", section, "academicYearId", academicYearId))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long createStudent(String admissionNo, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/students")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", fullName,
                                "admissionNo", admissionNo,
                                "dob", LocalDate.of(2012, 5, 20).toString(),
                                "admissionDate", LocalDate.of(2018, 6, 1).toString(),
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long createUser(String phone, String email, String name, Role role) {
        return ensureUser(phone, email, name, role).getId();
    }

    private long readId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private User ensureUser(String phone, String email, String name, Role... roles) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setName(name);
            user.setActive(true);
            User saved = userRepository.saveAndFlush(user);
            for (Role role : roles) {
                UserRole userRole = new UserRole();
                userRole.setUserId(saved.getId());
                userRole.setRole(role);
                userRoleRepository.saveAndFlush(userRole);
            }
            return saved;
        });
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
