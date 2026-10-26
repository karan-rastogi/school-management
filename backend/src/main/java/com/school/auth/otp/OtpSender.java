package com.school.auth.otp;

import java.time.Duration;

public interface OtpSender {

    void send(String phone, String code, Duration validity);
}
