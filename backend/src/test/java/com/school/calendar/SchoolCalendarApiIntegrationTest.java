package com.school.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.auth.security.JwtService;
import com.school.calendar.repository.SchoolCalendarRepository;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SchoolCalendarApiIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final LocalDate WEEK_START = LocalDate.of(2026, 1, 5);
    private static final LocalDate WEEK_END = LocalDate.of(2026, 1, 11);

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
    private SchoolCalendarRepository schoolCalendarRepository;

    private String adminToken;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.issueAccessToken(
                ensureUser("+919333000001", "cal-admin@school.test", "Calendar Admin", Role.ADMIN).getId(),
                Role.ADMIN);
        teacherToken = jwtService.issueAccessToken(
                ensureUser("+919333000002", "cal-teacher@school.test", "Calendar Teacher", Role.TEACHER).getId(),
                Role.TEACHER);

        schoolCalendarRepository.deleteAll(schoolCalendarRepository
                .findByDateBetweenOrderByDateAsc(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12)));
    }

    @Test
    void calendarEndpointsRequireAnAdmin() throws Exception {
        mockMvc.perform(get("/admin/calendar?from=2026-01-05&to=2026-01-11"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/calendar?from=2026-01-05&to=2026-01-11").cookie(teacherCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/admin/calendar/2026-01-06").cookie(teacherCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "HOLIDAY"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDateCanBeMarkedAsAHolidayAndTheRangeReflectsIt() throws Exception {
        mockMvc.perform(put("/admin/calendar/2026-01-06").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "HOLIDAY", "description", "Founder's day"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-01-06"))
                .andExpect(jsonPath("$.type").value("HOLIDAY"))
                .andExpect(jsonPath("$.working").value(false))
                .andExpect(jsonPath("$.description").value("Founder's day"));

        mockMvc.perform(get("/admin/calendar?from=2026-01-05&to=2026-01-11").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].date").value("2026-01-05"))
                .andExpect(jsonPath("$[0].working").value(true))
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[1].type").value("HOLIDAY"))
                .andExpect(jsonPath("$[1].working").value(false))
                .andExpect(jsonPath("$[5].working").value(false))
                .andExpect(jsonPath("$[6].working").value(false));
    }

    @Test
    void sundaysAndSaturdaysAreNonWorkingByDefaultButOverridable() throws Exception {
        mockMvc.perform(get("/admin/calendar/2026-01-04").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(false))
                .andExpect(jsonPath("$.type").doesNotExist());

        mockMvc.perform(put("/admin/calendar/2026-01-04").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "WORKING", "description", "Sports day"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true));
    }

    @Test
    void aWorkingSaturdayIsCountedByTheWorkingDaysEndpoint() throws Exception {
        mockMvc.perform(put("/admin/calendar/2026-01-10").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "WORKING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true));

        mockMvc.perform(get("/admin/calendar/working-days?from=2026-01-05&to=2026-01-11").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-01-05"))
                .andExpect(jsonPath("$.to").value("2026-01-11"))
                .andExpect(jsonPath("$.workingDays").value(6));
    }

    @Test
    void workingDaysExcludeHolidaysAndHalfDaysAndExamDaysStillCount() throws Exception {
        mockMvc.perform(put("/admin/calendar/2026-01-06").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "HOLIDAY"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/admin/calendar/2026-01-07").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "EXAM"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/calendar/working-days?from=2026-01-05&to=2026-01-11").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(4));
    }

    @Test
    void deletingAnEntryRestoresTheDefault() throws Exception {
        mockMvc.perform(put("/admin/calendar/2026-01-06").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "HOLIDAY"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/admin/calendar/2026-01-06").cookie(adminCookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/calendar/2026-01-06").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.working").value(true));
    }

    @Test
    void anInvalidRangeIsRejected() throws Exception {
        mockMvc.perform(get("/admin/calendar?from=2026-01-11&to=2026-01-05").cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void markingWithoutATypeIsRejected() throws Exception {
        mockMvc.perform(put("/admin/calendar/2026-01-06").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "no type"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Cookie adminCookie() {
        return new Cookie(ACCESS_COOKIE, adminToken);
    }

    private Cookie teacherCookie() {
        return new Cookie(ACCESS_COOKIE, teacherToken);
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
