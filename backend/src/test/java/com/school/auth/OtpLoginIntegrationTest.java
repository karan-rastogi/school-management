package com.school.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.auth.domain.OtpCode;
import com.school.auth.otp.OtpSender;
import com.school.auth.repository.OtpCodeRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the phone-OTP login rules that need the real persistence layer: a correct
 * code signs the user in, an expired code is refused and must be re-requested, and after the
 * configured number of wrong guesses the code is refused even when the right value is finally
 * supplied. The rate limits are raised so they do not interfere with the attempt-limit assertions.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.otp.rate-limit.ip-requests=100000",
        "app.auth.otp.rate-limit.ip-verifies=100000",
        "app.auth.otp.rate-limit.phone-requests=100000",
        "app.auth.otp.rate-limit.phone-verifies=100000"
})
class OtpLoginIntegrationTest extends AbstractPostgresTest {

    private static final String PHONE = "+919000000301";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @MockitoBean
    private OtpSender otpSender;

    private final Map<String, String> sentCodes = new HashMap<>();

    @BeforeEach
    void setUp() {
        sentCodes.clear();
        otpCodeRepository.deleteAll();
        doAnswer(invocation -> {
            sentCodes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(otpSender).send(any(), any(), any());

        ensureUser(PHONE, "otp-login@school.test", "Otp Login", true, Role.ADMIN);
    }

    @Test
    void aCorrectCodeSignsTheUserIn() throws Exception {
        requestCode();

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", PHONE, "code", sentCodes.get(PHONE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"));
    }

    @Test
    void anExpiredCodeIsRejectedAndANewRequestIsNeeded() throws Exception {
        requestCode();
        expireStoredCode();

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", PHONE, "code", sentCodes.get(PHONE)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

        // Requesting again issues a fresh, usable code.
        requestCode();
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", PHONE, "code", sentCodes.get(PHONE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"));
    }

    @Test
    void afterFiveWrongGuessesTheCodeIsRefusedEvenWhenTheRightValueIsGiven() throws Exception {
        requestCode();
        String correct = sentCodes.get(PHONE);
        String wrong = "000000".equals(correct) ? "111111" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("phone", PHONE, "code", wrong))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", PHONE, "code", correct))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    private void requestCode() throws Exception {
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", PHONE))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(sentCodes.get(PHONE)).as("an OTP should have been sent to %s", PHONE).isNotNull();
    }

    private void expireStoredCode() {
        OtpCode stored = otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE).orElseThrow();
        stored.setExpiresAt(OffsetDateTime.now(clock).minusMinutes(1));
        otpCodeRepository.saveAndFlush(stored);
    }

    private User ensureUser(String phone, String email, String name, boolean active, Role... roles) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setName(name);
            user.setActive(active);
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
