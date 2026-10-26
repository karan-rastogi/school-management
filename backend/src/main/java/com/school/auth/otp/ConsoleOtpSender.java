package com.school.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.auth.otp.sender", havingValue = "console", matchIfMissing = true)
public class ConsoleOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    @Override
    public void send(String phone, String code, Duration validity) {
        log.info("OTP (dev console sender) for {} is {} and is valid for {} minutes. "
                        + "Configure app.auth.otp.sender=sms for a real provider.",
                mask(phone), code, validity.toMinutes());
    }

    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
