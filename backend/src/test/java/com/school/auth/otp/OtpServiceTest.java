package com.school.auth.otp;

import com.school.auth.config.AuthProperties;
import com.school.auth.domain.OtpCode;
import com.school.auth.repository.OtpCodeRepository;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.ratelimit.RateLimiter;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final String PHONE = "+919876543210";
    private static final String OTHER_PHONE = "+919876500000";
    private static final String CLIENT_IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-10-27T06:00:00Z");

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpSender otpSender;

    private PasswordEncoder passwordEncoder;
    private Clock clock;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        otpService = serviceWith(authProperties(5, 3, 100, 5, 100));
    }

    private OtpService serviceWith(AuthProperties properties) {
        return new OtpService(
                otpCodeRepository,
                userRepository,
                otpSender,
                new RateLimiter(clock),
                passwordEncoder,
                properties,
                clock);
    }

    @Test
    void requestStoresOnlyAHashOfTheCodeAndSendsItThroughTheConfiguredSender() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(activeUser()));

        otpService.requestCode(PHONE, CLIENT_IP);

        ArgumentCaptor<OtpCode> storedCode = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(storedCode.capture());

        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(otpSender).send(any(), sentCode.capture(), any());

        OtpCode persisted = storedCode.getValue();
        String plainCode = sentCode.getValue();

        assertThat(plainCode).hasSize(6).containsOnlyDigits();
        assertThat(persisted.getCodeHash()).isNotEqualTo(plainCode);
        assertThat(persisted.getCodeHash()).doesNotContain(plainCode);
        assertThat(passwordEncoder.matches(plainCode, persisted.getCodeHash())).isTrue();
        assertThat(persisted.getPhone()).isEqualTo(PHONE);
        assertThat(persisted.getAttempts()).isZero();
        assertThat(persisted.getExpiresAt())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plus(Duration.ofMinutes(5)));
    }

    @Test
    void requestOnlyKeepsTheNewestCodeForAPhone() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(activeUser()));

        otpService.requestCode(PHONE, CLIENT_IP);

        var ordered = org.mockito.Mockito.inOrder(otpCodeRepository);
        ordered.verify(otpCodeRepository).deleteByPhone(PHONE);
        ordered.verify(otpCodeRepository).save(any(OtpCode.class));
    }

    @Test
    void requestDoesNotSendACodeForAnUnknownNumber() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        otpService.requestCode(PHONE, CLIENT_IP);

        verify(otpCodeRepository, never()).save(any(OtpCode.class));
        verify(otpSender, never()).send(any(), any(), any());
    }

    @Test
    void requestDoesNotSendACodeForAnInactiveAccount() {
        User inactive = activeUser();
        inactive.setActive(false);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(inactive));

        otpService.requestCode(PHONE, CLIENT_IP);

        verify(otpCodeRepository, never()).save(any(OtpCode.class));
        verify(otpSender, never()).send(any(), any(), any());
    }

    @Test
    void requestIsRateLimitedPerPhone() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(activeUser()));

        otpService.requestCode(PHONE, CLIENT_IP);
        otpService.requestCode(PHONE, CLIENT_IP);
        otpService.requestCode(PHONE, CLIENT_IP);

        assertThatThrownBy(() -> otpService.requestCode(PHONE, CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void requestIsRateLimitedPerClientAddress() {
        when(userRepository.findByPhone(any())).thenReturn(Optional.of(activeUser()));

        for (int attempt = 0; attempt < 5; attempt++) {
            otpService.requestCode("+9198765000" + attempt, CLIENT_IP);
        }

        ApiException thrown = (ApiException) org.assertj.core.api.Assertions
                .catchThrowable(() -> otpService.requestCode("+919876500099", CLIENT_IP));

        assertThat(thrown.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void verifyAcceptsTheCorrectCodeAndConsumesIt() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(activeUser()));
        OtpCode stored = storedCode("123456", 0, futureExpiry());
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).thenReturn(Optional.of(stored));

        User authenticated = otpService.verifyCode(PHONE, "123456", CLIENT_IP);

        assertThat(authenticated.getPhone()).isEqualTo(PHONE);
        verify(otpCodeRepository).delete(stored);
        verify(otpCodeRepository, never()).saveAndFlush(any(OtpCode.class));
    }

    @Test
    void verifyRejectsAWrongCodeAndRecordsTheAttempt() {
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE))
                .thenReturn(Optional.of(storedCode("123456", 0, futureExpiry())));

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "000000", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.OTP_INVALID));

        verify(otpCodeRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(otp -> otp.getAttempts() == 1));
    }

    @Test
    void verifyAllowsFiveAttemptsAndThenRefusesEvenTheCorrectCode() {
        OtpCode stored = storedCode("123456", 0, futureExpiry());
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).thenReturn(Optional.of(stored));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> otpService.verifyCode(PHONE, "000000", CLIENT_IP))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.OTP_INVALID));
        }
        assertThat(stored.getAttempts()).isEqualTo(5);

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                        .isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED));
    }

    @Test
    void verifyRejectsAnExpiredCodeAndRemovesIt() {
        OtpCode expired = storedCode("123456", 0, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusSeconds(1));
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.OTP_EXPIRED));

        verify(otpCodeRepository).delete(expired);
    }

    @Test
    void verifyRejectsWhenNoCodeHasBeenRequested() {
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.OTP_INVALID));
    }

    @Test
    void verifyRejectsTheRightCodeForAnAccountThatWasDeactivatedInTheMeantime() {
        User inactive = activeUser();
        inactive.setActive(false);
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE))
                .thenReturn(Optional.of(storedCode("123456", 0, futureExpiry())));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                        .isEqualTo(ErrorCode.ACCOUNT_NOT_REGISTERED));
    }

    @Test
    void verifyIsRateLimitedPerPhone() {
        OtpService limitedService = serviceWith(authProperties(5, 3, 3, 5, 100));
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE))
                .thenReturn(Optional.of(storedCode("123456", 0, futureExpiry())));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> limitedService.verifyCode(PHONE, "000000", CLIENT_IP))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                            .isEqualTo(ErrorCode.OTP_INVALID));
        }

        ApiException thrown = (ApiException) org.assertj.core.api.Assertions
                .catchThrowable(() -> limitedService.verifyCode(PHONE, "000000", CLIENT_IP));

        assertThat(thrown.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void verifyRejectsAMalformedPhoneNumber() {
        assertThatThrownBy(() -> otpService.verifyCode("not-a-phone", "123456", CLIENT_IP))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void verifyKeepsRateLimitBucketsSeparateForDifferentPhones() {
        when(otpCodeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456", CLIENT_IP)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> otpService.verifyCode(OTHER_PHONE, "123456", CLIENT_IP)).isInstanceOf(ApiException.class);

        verify(otpCodeRepository).findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE);
        verify(otpCodeRepository).findFirstByPhoneOrderByCreatedAtDescIdDesc(OTHER_PHONE);
    }

    private OtpCode storedCode(String plainCode, int attempts, OffsetDateTime expiresAt) {
        OtpCode otpCode = new OtpCode();
        otpCode.setId(1L);
        otpCode.setPhone(PHONE);
        otpCode.setCodeHash(passwordEncoder.encode(plainCode));
        otpCode.setAttempts(attempts);
        otpCode.setExpiresAt(expiresAt);
        return otpCode;
    }

    private OffsetDateTime futureExpiry() {
        return OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plus(Duration.ofMinutes(4));
    }

    private User activeUser() {
        User user = new User();
        user.setId(42L);
        user.setName("Priya Sharma");
        user.setPhone(PHONE);
        user.setActive(true);
        return user;
    }

    private AuthProperties authProperties(
            int maxAttempts,
            int phoneRequests,
            int phoneVerifies,
            int ipRequests,
            int ipVerifies
    ) {
        return new AuthProperties(
                "http://localhost:5173",
                new AuthProperties.Jwt(
                        "test-secret-that-is-long-enough-for-hs256-0123456789",
                        "school-management",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30)),
                new AuthProperties.Cookie(false, "Lax"),
                new AuthProperties.Otp(
                        Duration.ofMinutes(5),
                        6,
                        maxAttempts,
                        "console",
                        new AuthProperties.RateLimit(
                                phoneRequests, phoneVerifies, ipRequests, ipVerifies, Duration.ofMinutes(15))));
    }
}
