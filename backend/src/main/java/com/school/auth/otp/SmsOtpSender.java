package com.school.auth.otp;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.auth.otp.sender", havingValue = "sms")
public class SmsOtpSender implements OtpSender {

    @Override
    public void send(String phone, String code, Duration validity) {
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                ErrorCode.NOT_IMPLEMENTED,
                "The SMS provider is not wired up yet. Set app.auth.otp.sender=console for local development.");
    }
}
