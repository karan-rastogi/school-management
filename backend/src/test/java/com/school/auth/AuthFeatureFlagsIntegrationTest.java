package com.school.auth;

import com.school.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the default flags, Google and phone OTP stay on while password login stays off, so the
 * {@code /auth/login} endpoint does not exist (404) and the public config reflects it.
 */
@AutoConfigureMockMvc
class AuthFeatureFlagsIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDefaultFlagsEnableOAuthAndOtpButNotPasswordLogin() throws Exception {
        mockMvc.perform(get("/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordLoginEnabled").value(false))
                .andExpect(jsonPath("$.oauthEnabled").value(true))
                .andExpect(jsonPath("$.otpEnabled").value(true));
    }

    @Test
    void thePasswordLoginEndpointDoesNotExistWhenItsFlagIsOff() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@school.test\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isNotFound());
    }
}
