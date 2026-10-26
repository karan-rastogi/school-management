package com.school.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyBody(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9\\s().-]{8,25}$", message = "must be a valid phone number")
        String phone,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4,10}$", message = "must be a numeric verification code")
        String code
) {
}
