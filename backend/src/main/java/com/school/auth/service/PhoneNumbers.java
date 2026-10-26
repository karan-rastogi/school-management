package com.school.auth.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

public final class PhoneNumbers {

    private static final Pattern ALLOWED = Pattern.compile("^\\+?[0-9]{8,15}$");

    private PhoneNumbers() {
    }

    public static String normalize(String rawPhone) {
        if (rawPhone == null) {
            throw invalid();
        }
        String candidate = rawPhone.trim().replaceAll("[\\s().-]", "");
        if (!ALLOWED.matcher(candidate).matches()) {
            throw invalid();
        }
        return candidate;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Enter a valid phone number.");
    }
}
