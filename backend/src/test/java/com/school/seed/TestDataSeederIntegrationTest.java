package com.school.seed;

import com.school.academic.domain.AcademicYear;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceRepository;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.student.domain.Student;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the application against a dedicated, empty PostgreSQL so the startup seeder runs exactly as
 * it would on a fresh database, and verifies the full test data set. The seeder is only gated by
 * {@code app.auth.password-login.enabled} plus an empty database; the password comes from
 * {@code SEED_TEST_PASSWORD} and is never hard-coded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.password-login.enabled=true",
        "SEED_TEST_PASSWORD=seed-pass-12345",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Lax"
})
@EnabledIf("embeddedPostgresAvailable")
class TestDataSeederIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startEmbedded();

    private static EmbeddedPostgres startEmbedded() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (Exception failure) {
            return null;
        }
    }

    static boolean embeddedPostgresAvailable() {
        return POSTGRES != null;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void theSeederCreatesTheFullTestDataSetWhenTheDatabaseIsEmpty() {
        assertThat(userRepository.count()).as("7 seeded accounts").isEqualTo(7);

        List<String> emails = List.of(
                "admin@test.com", "classteacher@test.com", "maths@test.com",
                "hindi@test.com", "parent1@test.com", "parent2@test.com", "parent3@test.com");
        for (String email : emails) {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(
                    () -> new AssertionError("missing seeded user " + email));
            assertThat(user.isActive()).isTrue();
            assertThat(user.isMustChangePassword()).isFalse();
            assertThat(user.getPasswordHash()).isNotNull().doesNotContain("seed-pass-12345");
            assertThat(passwordEncoder.matches("seed-pass-12345", user.getPasswordHash()))
                    .as("%s can sign in with the seed password", email)
                    .isTrue();
            assertThat(userRoleRepository.findByUserId(user.getId())).hasSize(1);
        }

        assertThat(userRepository.findByEmailIgnoreCase("admin@test.com").orElseThrow())
                .extracting(u -> userRoleRepository.findByUserId(u.getId()).get(0).getRole())
                .isEqualTo(Role.ADMIN);

        AcademicYear year = academicYearRepository.findByCurrentIsTrue().orElseThrow();
        assertThat(year.isCurrent()).isTrue();
        assertThat(schoolClassRepository
                .findFirstByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearCurrentIsTrue("6", "A")).isPresent();
        assertThat(schoolClassRepository
                .findFirstByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearCurrentIsTrue("7", "A")).isPresent();

        List<Student> students = studentRepository.findAll();
        assertThat(students).hasSize(8);
        assertThat(students).allSatisfy(student -> assertThat(student.getQrToken()).isNotNull());
        assertThat(students.stream().map(Student::getQrToken).distinct()).hasSize(8);

        assertThat(studentEnrollmentRepository.count()).isEqualTo(8);
        assertThat(teacherAssignmentRepository.count()).isEqualTo(4);
        assertThat(schoolCalendarRepository.count()).isEqualTo(3);

        User parent1 = userRepository.findByEmailIgnoreCase("parent1@test.com").orElseThrow();
        User parent2 = userRepository.findByEmailIgnoreCase("parent2@test.com").orElseThrow();
        User parent3 = userRepository.findByEmailIgnoreCase("parent3@test.com").orElseThrow();
        assertThat(parentStudentLinkRepository.findByParentUserId(parent1.getId())).hasSize(2);
        assertThat(parentStudentLinkRepository.findByParentUserId(parent2.getId())).hasSize(1);
        assertThat(parentStudentLinkRepository.findByParentUserId(parent3.getId())).hasSize(1);

        var attendance = attendanceRepository.findAll();
        assertThat(attendance).as("10 days x 8 students minus 3 unmarked").hasSize(77);
        assertThat(attendance.stream().map(a -> a.getDate()).distinct()).hasSize(10);
        assertThat(attendance.stream().map(a -> a.getStatus()).distinct())
                .contains(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT,
                        AttendanceStatus.LATE, AttendanceStatus.HALF_DAY);
    }

    @Test
    void aSeededAccountCanActuallySignIn() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"seed-pass-12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }
}
