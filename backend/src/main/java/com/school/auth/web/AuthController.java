package com.school.auth.web;

import com.school.auth.otp.OtpService;
import com.school.auth.security.AuthenticatedUser;
import com.school.auth.service.AuthService;
import com.school.auth.web.dto.CurrentUserResponse;
import com.school.auth.web.dto.OtpRequestBody;
import com.school.auth.web.dto.OtpRequestResponse;
import com.school.auth.web.dto.OtpVerifyBody;
import com.school.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;

    public AuthController(OtpService otpService, AuthService authService) {
        this.otpService = otpService;
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestBody body, HttpServletRequest request) {
        otpService.requestCode(body.phone(), clientAddress(request));
        return new OtpRequestResponse("ACCEPTED",
                "If this number belongs to an active account, a verification code has been sent.");
    }

    @PostMapping("/otp/verify")
    public CurrentUserResponse verifyOtp(
            @Valid @RequestBody OtpVerifyBody body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user = otpService.verifyCode(body.phone(), body.code(), clientAddress(request));
        AuthenticatedUser principal = authService.startSession(user, response);
        return CurrentUserResponse.from(principal);
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

    private static String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
