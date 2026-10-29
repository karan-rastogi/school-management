package com.school.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Development OTP sender: it writes to the application log instead of sending an SMS.
 *
 * <p>By default it does <strong>not</strong> write the verification code, because the code is a
 * single-use credential and logs must not carry credentials. Set
 * {@code app.auth.otp.console-expose-code=true} (development only) when a developer needs to read
 * the code from the console.
 */
@Component
@ConditionalOnProperty(name = "app.auth.otp.sender", havingValue = "console", matchIfMissing = true)
public class ConsoleOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    private final boolean exposeCode;

    public ConsoleOtpSender(@Value("${app.auth.otp.console-expose-code:false}") boolean exposeCode) {
        this.exposeCode = exposeCode;
    }

    @Override
    public void send(String phone, String code, Duration validity) {
        if (exposeCode) {
            log.warn("OTP (dev console sender) for {} is {} and is valid for {} minutes. "
                            + "Never enable app.auth.otp.console-expose-code outside development.",
                    mask(phone), code, validity.toMinutes());
        } else {
            log.info("OTP (dev console sender) sent a code for {} valid for {} minutes. "
                            + "The code is not logged; set app.auth.otp.console-expose-code=true "
                            + "(development only) to print it.",
                    mask(phone), validity.toMinutes());
        }
    }

    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
