package com.school.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.audit.domain.AuditLog;
import com.school.audit.repository.AuditLogRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditLogIntegrationTest extends AbstractPostgresTest {

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

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void signInAsAdmin() {
        admin = ensureUser("+919222000001", "audit-admin@school.test", "Audit Admin", Role.ADMIN);
        adminToken = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    void roleChangesAreAuditedWithActorAndEntity() throws Exception {
        User target = ensureUser("+919222000002", "audit-target@school.test", "Audit Target", Role.PARENT);

        mockMvc.perform(put("/admin/users/" + target.getId() + "/roles")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", Set.of("TEACHER", "PARENT")))))
                .andExpect(status().isOk());

        AuditLog entry = latestFor("USER_ROLES_CHANGED", String.valueOf(target.getId()));
        assertThat(entry).isNotNull();
        assertThat(entry.getActorUserId()).isEqualTo(admin.getId());
        assertThat(entry.getDetails()).contains("from=").contains("to=");
    }

    @Test
    void qrTokenRegenerationIsAudited() throws Exception {
        Student student = new Student();
        student.setFullName("Audit Pupil");
        student.setAdmissionNo("ADM-AUDIT-QR");
        student.setDob(LocalDate.of(2015, 6, 1));
        student.setAdmissionDate(LocalDate.of(2021, 4, 1));
        Student saved = studentRepository.saveAndFlush(student);

        mockMvc.perform(post("/admin/students/" + saved.getId() + "/qr-token").cookie(adminCookie()))
                .andExpect(status().isOk());

        AuditLog entry = latestFor("QR_TOKEN_REGENERATED", String.valueOf(saved.getId()));
        assertThat(entry).isNotNull();
        assertThat(entry.getActorUserId()).isEqualTo(admin.getId());
    }

    private AuditLog latestFor(String action, String entityId) {
        List<AuditLog> matches = auditLogRepository.findAll().stream()
                .filter(log -> action.equals(log.getAction()) && entityId.equals(log.getEntityId()))
                .toList();
        assertThat(matches).as("audit rows for %s/%s", action, entityId).isNotEmpty();
        return matches.get(matches.size() - 1);
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
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
}
