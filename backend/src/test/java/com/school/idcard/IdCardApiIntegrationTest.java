package com.school.idcard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.school.AbstractPostgresTest;
import com.school.auth.security.JwtService;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdCardApiIntegrationTest extends AbstractPostgresTest {

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

    private User admin;
    private String adminToken;

    @BeforeEach
    void signInAsAdmin() {
        admin = ensureUser("+919111700001", "idcard-admin@school.test", "ID Card Admin", Role.ADMIN);
        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    void adminDownloadsASingleStudentIdCardAsPdf() throws Exception {
        long studentId = createStudent("ADM-IDC-1", "Aarav Sharma");

        MvcResult result = mockMvc.perform(get("/admin/id-cards/students/" + studentId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        Matchers.allOf(
                                Matchers.containsString("attachment"),
                                Matchers.containsString("ADM-IDC-1-id-card.pdf"))))
                .andReturn();

        assertThat(isPdf(result)).isTrue();
    }

    @Test
    void adminDownloadsAClassSheetListingEveryEnrolledStudent() throws Exception {
        long yearId = createAcademicYear("AY-IDC-Class", "2016-04-01", "2017-03-31");
        long classId = createClass("Grade 5", "C", yearId);
        long first = createStudent("ADM-IDC-2", "Sheet Student One");
        long second = createStudent("ADM-IDC-3", "Sheet Student Two");
        enroll(first, classId, 1);
        enroll(second, classId, 2);

        MvcResult result = mockMvc.perform(get("/admin/id-cards/classes/" + classId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("Grade-5-C-id-cards.pdf")))
                .andReturn();

        assertThat(isPdf(result)).isTrue();

        PdfReader reader = new PdfReader(result.getResponse().getContentAsByteArray());
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void idCardEndpointsRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(get("/admin/id-cards/students/1"))
                .andExpect(status().isUnauthorized());

        User teacher = ensureUser("+919111700002", "idcard-teacher@school.test", "ID Card Teacher", Role.TEACHER);
        String teacherToken = jwtService.issueAccessToken(teacher.getId(), Role.TEACHER);

        mockMvc.perform(get("/admin/id-cards/classes/1").cookie(new Cookie(ACCESS_COOKIE, teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void aClassSheetIsNotFoundWhenNoStudentsAreEnrolled() throws Exception {
        long yearId = createAcademicYear("AY-IDC-Empty", "2016-04-01", "2017-03-31");
        long classId = createClass("Grade 11", "Z", yearId);

        mockMvc.perform(get("/admin/id-cards/classes/" + classId).cookie(adminCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aStudentCardIsNotFoundForAnUnknownStudent() throws Exception {
        mockMvc.perform(get("/admin/id-cards/students/999999").cookie(adminCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static boolean isPdf(MvcResult result) {
        byte[] body = result.getResponse().getContentAsByteArray();
        return body.length > 5 && "%PDF-".equals(new String(body, 0, 5, StandardCharsets.ISO_8859_1));
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private long createAcademicYear(String name, String start, String end) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/academic-years")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "startDate", start, "endDate", end, "current", false))))
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
                                "dob", LocalDate.of(2010, 5, 20).toString(),
                                "admissionDate", LocalDate.of(2016, 6, 1).toString(),
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private void enroll(long studentId, long classId, int rollNo) throws Exception {
        mockMvc.perform(post("/admin/students/" + studentId + "/enrollments")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classId", classId, "rollNo", rollNo, "fromDate", "2016-06-01"))))
                .andExpect(status().isCreated());
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
