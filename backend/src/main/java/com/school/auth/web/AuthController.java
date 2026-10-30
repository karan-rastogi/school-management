package com.school.auth.web;

import com.school.auth.service.AuthService;
import com.school.auth.web.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The always-on session endpoints. The sign-in entry points are split into their own controllers,
 * each gated by its feature flag, so a disabled method returns 404 and its code is not reachable:
 * {@link OtpAuthController} for phone OTP and {@link PasswordAuthController} for email + password.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/refresh")
    public CurrentUserResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return CurrentUserResponse.from(authService.refreshSession(request, response));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.endSession(request, response);
    }
}
