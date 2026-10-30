package com.school.auth;

import com.school.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With every sign-in method disabled, each disabled method's endpoints answer 404 (the code is kept,
 * only gated), the public config reports all three off, and the OAuth client needs no real keys.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.password-login.enabled=false",
        "app.auth.oauth.enabled=false",
        "app.auth.otp.enabled=false"
})
class AuthDisabledMethodsIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thePublicConfigReportsEveryMethodDisabled() throws Exception {
        mockMvc.perform(get("/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordLoginEnabled").value(false))
                .andExpect(jsonPath("$.oauthEnabled").value(false))
                .andExpect(jsonPath("$.otpEnabled").value(false));
    }

    @Test
    void oauthEndpointsReturn404WhenOAuthIsDisabled() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
    }

    @Test
    void otpEndpointsReturn404WhenOtpIsDisabled() throws Exception {
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+919876543210\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePasswordLoginEndpointReturns404WhenItsFlagIsOff() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@school.test\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void logoutStaysAvailableRegardlessOfTheFlags() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
    }
}
