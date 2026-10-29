package com.school.auth;

import com.school.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sign-in entry point is rate limited per client address. With a limit of two, the third start
 * from the same address is refused with {@code 429 RATE_LIMITED}.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.rate-limit.oauth-starts-per-ip=2")
class OAuthRateLimitIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void repeatedSignInStartsFromOneAddressAreRateLimited() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
