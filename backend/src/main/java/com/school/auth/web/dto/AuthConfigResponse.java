package com.school.auth.web.dto;

public record AuthConfigResponse(
        boolean passwordLoginEnabled,
        boolean oauthEnabled,
        boolean otpEnabled
) {
}
