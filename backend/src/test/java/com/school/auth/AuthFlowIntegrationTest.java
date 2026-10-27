package com.school.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.auth.otp.OtpSender;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.otp.rate-limit.ip-requests=10000",
        "app.auth.otp.rate-limit.ip-verifies=10000",
        "app.auth.otp.rate-limit.phone-requests=10000",
        "app.auth.otp.rate-limit.phone-verifies=10000",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Lax"
})
class AuthFlowIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OtpSender otpSender;

    private final Map<String, String> sentCodes = new HashMap<>();

    @BeforeEach
    void captureOtpCodes() {
        sentCodes.clear();
        doAnswer(invocation -> {
            sentCodes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(otpSender).send(any(), any(), any());
    }

    @Test
    void phoneOtpLoginIssuesASecureHttpOnlySessionCookie() throws Exception {
        String phone = "+919000000101";
        ensureUser(phone, "admin101@school.test", "Asha Admin", true, Role.ADMIN);

        MvcResult result = requestAndVerify(phone);

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith(ACCESS_COOKIE + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/"));

        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith(REFRESH_COOKIE + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/auth"));
    }

    @Test
    void meReturnsTheSignedInUserAndTheirRoles() throws Exception {
        String phone = "+919000000102";
        ensureUser(phone, "admin102@school.test", "Asha Admin", true, Role.ADMIN);

        String accessToken = cookieValue(requestAndVerify(phone).getResponse(), ACCESS_COOKIE);

        mockMvc.perform(get("/me").cookie(new Cookie(ACCESS_COOKIE, accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Admin"))
                .andExpect(jsonPath("$.email").value("admin102@school.test"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.activeRole").value("ADMIN"));
    }

    @Test
    void meIsRejectedWithoutASession() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void adminEndpointsAreGuardedByRole() throws Exception {
        String adminPhone = "+919000000103";
        String teacherPhone = "+919000000104";
        ensureUser(adminPhone, "admin103@school.test", "Asha Admin", true, Role.ADMIN);
        ensureUser(teacherPhone, "teacher104@school.test", "Vikram Teacher", true, Role.TEACHER);

        String adminToken = cookieValue(requestAndVerify(adminPhone).getResponse(), ACCESS_COOKIE);
        String teacherToken = cookieValue(requestAndVerify(teacherPhone).getResponse(), ACCESS_COOKIE);

        mockMvc.perform(get("/admin/users").cookie(new Cookie(ACCESS_COOKIE, adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/admin/users").cookie(new Cookie(ACCESS_COOKIE, teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refreshRotatesTheRefreshTokenAndRejectsTheReusedOne() throws Exception {
        String phone = "+919000000105";
        ensureUser(phone, "admin105@school.test", "Asha Admin", true, Role.ADMIN);

        MvcResult login = requestAndVerify(phone);
        String originalRefresh = cookieValue(login.getResponse(), REFRESH_COOKIE);

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, originalRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"))
                .andReturn();

        String rotatedRefresh = cookieValue(refreshed.getResponse(), REFRESH_COOKIE);
        assertThat(rotatedRefresh).isNotEqualTo(originalRefresh);

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, originalRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutClearsTheCookiesAndRevokesTheRefreshToken() throws Exception {
        String phone = "+919000000106";
        ensureUser(phone, "admin106@school.test", "Asha Admin", true, Role.ADMIN);

        MvcResult login = requestAndVerify(phone);
        String refresh = cookieValue(login.getResponse(), REFRESH_COOKIE);

        MvcResult logout = mockMvc.perform(post("/auth/logout").cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith(ACCESS_COOKIE + "=").contains("Max-Age=0"));
        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith(REFRESH_COOKIE + "=").contains("Max-Age=0"));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void switchingActiveRoleOnlyAllowsRolesTheUserActuallyHolds() throws Exception {
        String phone = "+919000000107";
        User user = ensureUser(phone, "both107@school.test", "Dual Role", true, Role.ADMIN, Role.TEACHER);

        MvcResult login = requestAndVerify(phone);
        String accessToken = cookieValue(login.getResponse(), ACCESS_COOKIE);

        MvcResult switched = mockMvc.perform(post("/me/active-role")
                        .cookie(new Cookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("TEACHER"))
                .andReturn();

        String switchedToken = cookieValue(switched.getResponse(), ACCESS_COOKIE);
        mockMvc.perform(get("/me").cookie(new Cookie(ACCESS_COOKIE, switchedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("TEACHER"));

        String switchedRefresh = cookieValue(switched.getResponse(), REFRESH_COOKIE);
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, switchedRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("TEACHER"));

        mockMvc.perform(post("/me/active-role")
                        .cookie(new Cookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "PARENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(user.getId()).isNotNull();
    }

    @Test
    void anUnknownNumberGetsTheSameAnswerAsAKnownOneAndNoCodeIsSent() throws Exception {
        String knownPhone = "+919000000108";
        String unknownPhone = "+919000000109";
        ensureUser(knownPhone, "admin108@school.test", "Asha Admin", true, Role.ADMIN);

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", knownPhone))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", unknownPhone))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(sentCodes).containsKey(knownPhone);
        assertThat(sentCodes).doesNotContainKey(unknownPhone);
    }

    @Test
    void aDeactivatedAccountCannotCompleteAnOtpLogin() throws Exception {
        String phone = "+919000000110";
        ensureUser(phone, "left110@school.test", "Former Teacher", false, Role.TEACHER);

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isAccepted());

        assertThat(sentCodes).doesNotContainKey(phone);
    }

    @Test
    void aFormattedPhoneNumberIsAcceptedAndMatchedAgainstTheStoredOne() throws Exception {
        String storedPhone = "+919000000112";
        String typedPhone = "+91 90000 00112";
        ensureUser(storedPhone, "admin112@school.test", "Asha Admin", true, Role.ADMIN);

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", typedPhone))))
                .andExpect(status().isAccepted());

        String code = sentCodes.get(storedPhone);
        assertThat(code).as("the code should be keyed by the normalised number").isNotNull();

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", typedPhone, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"));
    }

    @Test
    void invalidOtpCodesAreRejected() throws Exception {
        String phone = "+919000000111";
        ensureUser(phone, "admin111@school.test", "Asha Admin", true, Role.ADMIN);

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void theGoogleAuthorizationEndpointRedirectsToGoogleWithoutAnyPersonalDataInTheQuery() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("@"))));
    }

    private MvcResult requestAndVerify(String phone) throws Exception {
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isAccepted());

        String code = sentCodes.get(phone);
        assertThat(code).as("an OTP should have been sent to %s", phone).isNotNull();

        return mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "code", code))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private User ensureUser(String phone, String email, String name, boolean active, Role... roles) {
        Optional<User> existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User();
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        user.setActive(active);
        User saved = userRepository.saveAndFlush(user);

        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setUserId(saved.getId());
            userRole.setRole(role);
            userRoleRepository.saveAndFlush(userRole);
        }
        return saved;
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        return response.getHeaders("Set-Cookie").stream()
                .filter(cookieHeader -> cookieHeader.startsWith(name + "="))
                .map(cookieHeader -> cookieHeader.substring(name.length() + 1, cookieHeader.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in the response"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
