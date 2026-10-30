package com.school.auth.web;

import com.school.auth.password.ChangePasswordRequest;
import com.school.auth.password.LoginRequest;
import com.school.auth.password.PasswordLoginService;
import com.school.auth.security.AuthenticatedUser;
import com.school.auth.web.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email + password sign-in and password change. Only created when
 * {@code app.auth.password-login.enabled=true}; otherwise these paths are unhandled (404).
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "app.auth.password-login.enabled", havingValue = "true")
public class PasswordAuthController {

    private final PasswordLoginService passwordLoginService;

    public PasswordAuthController(PasswordLoginService passwordLoginService) {
        this.passwordLoginService = passwordLoginService;
    }

    @PostMapping("/login")
    public CurrentUserResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedUser principal = passwordLoginService.login(
                body.email(), body.password(), clientAddress(request), response);
        return CurrentUserResponse.from(principal);
    }

    @PostMapping("/change-password")
    public CurrentUserResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest body
    ) {
        return CurrentUserResponse.from(
                passwordLoginService.changePassword(user, body.currentPassword(), body.newPassword()));
    }

    private static String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
