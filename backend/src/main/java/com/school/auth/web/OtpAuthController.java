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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phone OTP sign-in. Only created when {@code app.auth.otp.enabled=true} (the default); when it is
 * false the paths are unhandled and Spring returns 404.
 */
@RestController
@RequestMapping("/auth/otp")
@ConditionalOnProperty(name = "app.auth.otp.enabled", havingValue = "true", matchIfMissing = true)
public class OtpAuthController {

    private final OtpService otpService;
    private final AuthService authService;

    public OtpAuthController(OtpService otpService, AuthService authService) {
        this.otpService = otpService;
        this.authService = authService;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestBody body, HttpServletRequest request) {
        otpService.requestCode(body.phone(), clientAddress(request));
        return new OtpRequestResponse("ACCEPTED",
                "If this number belongs to an active account, a verification code has been sent.");
    }

    @PostMapping("/verify")
    public CurrentUserResponse verifyOtp(
            @Valid @RequestBody OtpVerifyBody body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user = otpService.verifyCode(body.phone(), body.code(), clientAddress(request));
        AuthenticatedUser principal = authService.startSession(user, response);
        return CurrentUserResponse.from(principal);
    }

    private static String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
