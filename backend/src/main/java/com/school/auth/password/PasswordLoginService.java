package com.school.auth.password;

import com.school.auth.security.AuthenticatedUser;
import com.school.auth.security.UserAccountService;
import com.school.auth.service.AuthService;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.ratelimit.RateLimiter;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

/**
 * Email + password sign-in. Only enabled with {@code app.auth.password-login.enabled=true}; it is
 * meant for local testing and must never be turned on together with the {@code prod} profile
 * (enforced at startup by {@link com.school.config.AuthFeatureGuard}).
 */
@Service
@ConditionalOnProperty(name = "app.auth.password-login.enabled", havingValue = "true")
public class PasswordLoginService {

    private static final Logger log = LoggerFactory.getLogger(PasswordLoginService.class);

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final LoginThrottle loginThrottle;
    private final int ipAttempts;
    private final int emailAttempts;
    private final Duration rateLimitWindow;
    private final String timingHash;

    public PasswordLoginService(
            UserRepository userRepository,
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            RateLimiter rateLimiter,
            LoginThrottle loginThrottle,
            @Value("${app.auth.password-login.rate-limit.ip-attempts:20}") int ipAttempts,
            @Value("${app.auth.password-login.rate-limit.email-attempts:10}") int emailAttempts,
            @Value("${app.auth.password-login.rate-limit.window:PT15M}") Duration rateLimitWindow
    ) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.loginThrottle = loginThrottle;
        this.ipAttempts = ipAttempts;
        this.emailAttempts = emailAttempts;
        this.rateLimitWindow = rateLimitWindow;
        // A throwaway hash so an unknown email costs roughly the same as a wrong password.
        this.timingHash = passwordEncoder.encode("timing-equalisation");
    }

    public AuthenticatedUser login(String email, String password, String clientIp, HttpServletResponse response) {
        String subject = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String candidate = password == null ? "" : password;

        rateLimiter.check("password:login:ip:" + clientIp, ipAttempts, rateLimitWindow);
        if (!subject.isEmpty()) {
            rateLimiter.check("password:login:email:" + subject, emailAttempts, rateLimitWindow);
        }
        loginThrottle.assertNotLocked(subject);

        User user = userRepository.findByEmailIgnoreCase(subject)
                .filter(User::isActive)
                .orElse(null);

        boolean matches;
        if (user != null && user.getPasswordHash() != null) {
            matches = passwordEncoder.matches(candidate, user.getPasswordHash());
        } else {
            passwordEncoder.matches(candidate, timingHash);
            matches = false;
        }

        if (!matches) {
            loginThrottle.recordFailure(subject);
            log.info("Rejected an email + password sign-in");
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, INVALID_CREDENTIALS_MESSAGE);
        }

        loginThrottle.recordSuccess(subject);
        log.info("Signed in user id {} with email + password", user.getId());
        return authService.startSession(user, response);
    }

    @Transactional
    public AuthenticatedUser changePassword(
            AuthenticatedUser current,
            String currentPassword,
            String newPassword
    ) {
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                        "Your session is no longer valid. Please sign in again."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);
        log.info("User id {} changed their password", user.getId());

        return userAccountService.loadActiveUser(user.getId(), current.activeRole());
    }
}
