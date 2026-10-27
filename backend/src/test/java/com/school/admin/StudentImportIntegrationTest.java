package com.school.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.auth.security.JwtService;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StudentImportIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String HEADER = row("admission_no", "full_name", "dob", "admission_date", "status",
            "class_name", "class_section", "roll_no", "parent_name", "parent_phone", "parent_email", "relationship");

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

    @Autowired
    private StudentEnrollmentRepository studentEnrollmentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    private String adminToken;
    private long classId;

    @BeforeEach
    void setUp() {
        User admin = ensureAdmin();
        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);

        academicYearRepository.findByCurrentIsTrue()
                .filter(existing -> !"Import Year".equalsIgnoreCase(existing.getName()))
                .ifPresent(existing -> {
                    existing.setCurrent(false);
                    academicYearRepository.saveAndFlush(existing);
                });

        AcademicYear year = academicYearRepository.findByNameIgnoreCase("Import Year").orElseGet(() -> {
            AcademicYear created = new AcademicYear();
            created.setName("Import Year");
            created.setStartDate(LocalDate.of(2019, 4, 1));
            created.setEndDate(LocalDate.of(2020, 3, 31));
            return academicYearRepository.saveAndFlush(created);
        });
        if (!year.isCurrent()) {
            year.setCurrent(true);
            academicYearRepository.saveAndFlush(year);
        }

        SchoolClass schoolClass = schoolClassRepository
                .findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId("Grade 1", "A", year.getId())
                .orElseGet(() -> {
                    SchoolClass created = new SchoolClass();
                    created.setName("Grade 1");
                    created.setSection("A");
                    created.setAcademicYear(year);
                    return schoolClassRepository.saveAndFlush(created);
                });

        classId = schoolClass.getId();
    }

    @Test
    void previewReportsRowLevelErrorsAndWritesNothing() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-P-1", "Aarav Sharma", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "",
                        "Sunita Sharma", "+919222000101", "", "MOTHER"),
                row("ADM-P-2", "", "", "", "", "", "", "", "", "", "", ""),
                row("ADM-P-3", "Bad Date Child", "01-99-2013", "2019-06-01", "ACTIVE", "", "", "",
                        "", "", "", ""),
                row("ADM-P-4", "Bad Parent Child", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "",
                        "Someone", "", "", ""));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.canCommit").value(false))
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.errorCount").value(3))
                .andExpect(jsonPath("$.studentsToCreate").value(1))
                .andExpect(jsonPath("$.rows[1].outcome").value("ERROR"))
                .andExpect(jsonPath("$.rows[1].messages.length()").value(3))
                .andExpect(jsonPath("$.rows[2].messages[0]").value(containsString("dob")))
                .andExpect(jsonPath("$.rows[3].messages[0]").value(containsString("parent_phone")));

        assertThat(studentRepository.findByAdmissionNoIgnoreCase("ADM-P-1")).isEmpty();
        assertThat(userRepository.findByPhone("+919222000101")).isEmpty();
    }

    @Test
    void commitCreatesStudentsParentsLinksAndEnrollments() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-C-1", "Aarav Sharma", "2013-04-01", "2019-06-01", "ACTIVE", "Grade 1", "A", "1",
                        "Sunita Sharma", "+919222000201", "sunita.sharma@school.test", "MOTHER"),
                row("ADM-C-1", "", "", "", "", "", "", "",
                        "Rakesh Sharma", "+919222000202", "rakesh.sharma@school.test", "FATHER"),
                row("ADM-C-2", "Diya Nair", "2014-07-15", "2020-06-01", "ACTIVE", "Grade 1", "A", "2",
                        "Sunita Sharma", "+919222000201", "", "MOTHER"));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.canCommit").value(true))
                .andExpect(jsonPath("$.studentsToCreate").value(2))
                .andExpect(jsonPath("$.parentsToCreate").value(2))
                .andExpect(jsonPath("$.linksToCreate").value(3))
                .andExpect(jsonPath("$.errorCount").value(0));

        Student aarav = studentRepository.findByAdmissionNoIgnoreCase("ADM-C-1").orElseThrow();
        Student diya = studentRepository.findByAdmissionNoIgnoreCase("ADM-C-2").orElseThrow();
        User sunita = userRepository.findByPhone("+919222000201").orElseThrow();
        User rakesh = userRepository.findByPhone("+919222000202").orElseThrow();

        assertThat(aarav.getQrToken()).isNotNull();
        assertThat(diya.getFullName()).isEqualTo("Diya Nair");

        assertThat(userRoleRepository.findByUserId(sunita.getId()))
                .extracting(UserRole::getRole).containsExactly(Role.PARENT);
        assertThat(userRoleRepository.findByUserId(rakesh.getId()))
                .extracting(UserRole::getRole).containsExactly(Role.PARENT);

        assertThat(parentStudentLinkRepository.findByStudentId(aarav.getId())).hasSize(2);
        assertThat(parentStudentLinkRepository.findByStudentId(diya.getId())).hasSize(1);

        List<StudentEnrollment> enrollments = studentEnrollmentRepository
                .findByStudentIdOrderByFromDateDesc(aarav.getId());
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).getSchoolClass().getId()).isEqualTo(classId);
        assertThat(enrollments.get(0).getRollNo()).isEqualTo(1);
        assertThat(enrollments.get(0).getFromDate()).isEqualTo(aarav.getAdmissionDate());
    }

    @Test
    void commitIsRefusedWhenAnyRowHasAnErrorAndNothingIsWritten() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-R-1", "Good Child", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "", "", "", "", ""),
                row("ADM-R-2", "Broken Child", "not-a-date", "2019-06-01", "ACTIVE", "", "", "", "", "", "", ""));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.canCommit").value(false))
                .andExpect(jsonPath("$.errorCount").value(1));

        assertThat(studentRepository.findByAdmissionNoIgnoreCase("ADM-R-1")).isEmpty();
        assertThat(studentRepository.findByAdmissionNoIgnoreCase("ADM-R-2")).isEmpty();
    }

    @Test
    void aSecondImportOfTheSameFileCreatesNothingNew() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-RE-1", "Reimport Child", "2013-04-01", "2019-06-01", "ACTIVE", "Grade 1", "A", "5",
                        "Reimport Parent", "+919222000301", "reimport@school.test", "MOTHER"));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true));

        Student student = studentRepository.findByAdmissionNoIgnoreCase("ADM-RE-1").orElseThrow();

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.studentsToCreate").value(0))
                .andExpect(jsonPath("$.parentsToCreate").value(0))
                .andExpect(jsonPath("$.linksToCreate").value(0))
                .andExpect(jsonPath("$.duplicateLinks").value(1))
                .andExpect(jsonPath("$.rows[0].outcome").value("DUPLICATE"));

        assertThat(parentStudentLinkRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(studentEnrollmentRepository.findByStudentIdOrderByFromDateDesc(student.getId())).hasSize(1);
    }

    @Test
    void anAddressAlreadyOnFileIsLinkedRatherThanDuplicated() throws Exception {
        User existingParent = userRepository.findByPhone("+919222000401").orElseGet(() -> {
            User parent = new User();
            parent.setName("Existing Parent");
            parent.setPhone("+919222000401");
            parent.setActive(true);
            return userRepository.saveAndFlush(parent);
        });

        String csv = csv(
                HEADER,
                row("ADM-L-1", "Linked Child", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "",
                        "", "+919222000401", "", "FATHER"));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.parentsToCreate").value(0))
                .andExpect(jsonPath("$.linksToCreate").value(1));

        assertThat(userRepository.findByPhone("+919222000401")).isPresent();
        assertThat(userRoleRepository.findByUserId(existingParent.getId()))
                .extracting(UserRole::getRole).contains(Role.PARENT);

        Student student = studentRepository.findByAdmissionNoIgnoreCase("ADM-L-1").orElseThrow();
        assertThat(parentStudentLinkRepository.findByStudentId(student.getId())).hasSize(1);
    }

    @Test
    void conflictingStudentDetailsForTheSameAdmissionNumberAreReported() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-X-1", "First Name", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "", "", "", "", ""),
                row("ADM-X-1", "Different Name", "2013-04-01", "2019-06-01", "ACTIVE", "", "", "", "", "", "", ""));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.rows[1].outcome").value("ERROR"))
                .andExpect(jsonPath("$.rows[1].messages[0]").value(containsString("differs from row 2")));
    }

    @Test
    void anUnknownClassInTheCurrentYearIsReportedPerRow() throws Exception {
        String csv = csv(
                HEADER,
                row("ADM-K-1", "No Class Child", "2013-04-01", "2019-06-01", "ACTIVE", "Grade 99", "Z", "",
                        "", "", "", ""));

        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", csv, "dryRun", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.rows[0].messages[0]").value(containsString("Grade 99")));
    }

    @Test
    void aFileWithoutTheRequiredColumnsIsRejected() throws Exception {
        mockMvc.perform(post("/admin/students/import")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", "admission_no,full_name\nADM-Z-1,Only Name", "dryRun", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("dob")));
    }

    @Test
    void theImportEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(post("/admin/students/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csv", HEADER, "dryRun", true))))
                .andExpect(status().isUnauthorized());
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private User ensureAdmin() {
        return userRepository.findByPhone("+919111000099").orElseGet(() -> {
            User admin = new User();
            admin.setName("Import Admin");
            admin.setPhone("+919111000099");
            admin.setEmail("import.admin@school.test");
            admin.setActive(true);
            User saved = userRepository.saveAndFlush(admin);
            UserRole userRole = new UserRole();
            userRole.setUserId(saved.getId());
            userRole.setRole(Role.ADMIN);
            userRoleRepository.saveAndFlush(userRole);
            return saved;
        });
    }

    private long readId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String csv(String... lines) {
        return String.join("\n", lines);
    }

    private static String row(String... fields) {
        return String.join(",", fields);
    }
}
