package com.school.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    public static ApiError of(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> fieldErrors
    ) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors)
        );
    }
}
