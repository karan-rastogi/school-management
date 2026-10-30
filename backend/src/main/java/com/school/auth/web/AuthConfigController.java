package com.school.auth.web;

import com.school.auth.web.dto.AuthConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public feature discovery for the sign-in page: it lets the SPA render only the methods that are
 * actually enabled without polling each endpoint.
 */
@RestController
public class AuthConfigController {

    private final boolean passwordLoginEnabled;
    private final boolean oauthEnabled;
    private final boolean otpEnabled;

    public AuthConfigController(
            @Value("${app.auth.password-login.enabled:false}") boolean passwordLoginEnabled,
            @Value("${app.auth.oauth.enabled:true}") boolean oauthEnabled,
            @Value("${app.auth.otp.enabled:true}") boolean otpEnabled
    ) {
        this.passwordLoginEnabled = passwordLoginEnabled;
        this.oauthEnabled = oauthEnabled;
        this.otpEnabled = otpEnabled;
    }

    @GetMapping("/auth/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(passwordLoginEnabled, oauthEnabled, otpEnabled);
    }
}
