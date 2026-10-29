package com.school.auth.otp;

import com.school.auth.config.AuthProperties;
import com.school.auth.domain.OtpCode;
import com.school.auth.repository.OtpCodeRepository;
import com.school.auth.service.PhoneNumbers;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.ratelimit.RateLimiter;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final OtpSender otpSender;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            OtpCodeRepository otpCodeRepository,
            UserRepository userRepository,
            OtpSender otpSender,
            RateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.otpCodeRepository = otpCodeRepository;
        this.userRepository = userRepository;
        this.otpSender = otpSender;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional
    public void requestCode(String rawPhone, String clientIp) {
        String phone = PhoneNumbers.normalize(rawPhone);
        AuthProperties.RateLimit limit = authProperties.otp().rateLimit();

        rateLimiter.check("otp:request:phone:" + phone, limit.phoneRequests(), limit.window());
        rateLimiter.check("otp:request:ip:" + clientIp, limit.ipRequests(), limit.window());

        otpCodeRepository.deleteByPhone(phone);

        userRepository.findByPhone(phone)
                .filter(User::isActive)
                .ifPresent(user -> issueCode(phone));
    }

    public User verifyCode(String rawPhone, String code, String clientIp) {
        String phone = PhoneNumbers.normalize(rawPhone);
        String candidate = code == null ? "" : code.trim();
        AuthProperties.RateLimit limit = authProperties.otp().rateLimit();

        rateLimiter.check("otp:verify:phone:" + phone, limit.phoneVerifies(), limit.window());
        rateLimiter.check("otp:verify:ip:" + clientIp, limit.ipVerifies(), limit.window());

        OtpCode otp = otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OTP_INVALID,
                        "No verification code is pending for this number. Request a new one."));

        if (!otp.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            otpCodeRepository.delete(otp);
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OTP_EXPIRED,
                    "The verification code has expired. Request a new one.");
        }

        int maxAttempts = authProperties.otp().maxAttempts();
        if (otp.getAttempts() >= maxAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Too many incorrect attempts. Request a new verification code.");
        }

        if (!passwordEncoder.matches(candidate, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.saveAndFlush(otp);
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OTP_INVALID,
                    "The verification code is incorrect.");
        }

        otpCodeRepository.delete(otp);

        return userRepository.findByPhone(phone)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_NOT_REGISTERED,
                        "This number is not linked to an active account."));
    }

    private void issueCode(String phone) {
        String code = generateCode();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(authProperties.otp().ttl());

        OtpCode otpCode = new OtpCode();
        otpCode.setPhone(phone);
        otpCode.setCodeHash(passwordEncoder.encode(code));
        otpCode.setExpiresAt(expiresAt);
        otpCode.setAttempts(0);

        otpCodeRepository.save(otpCode);
        otpSender.send(phone, code, authProperties.otp().ttl());
    }

    private String generateCode() {
        int length = authProperties.otp().length();
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
