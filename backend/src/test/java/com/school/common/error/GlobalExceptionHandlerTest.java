package com.school.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsItsStatusAndCode() {
        ApiException exception = new ApiException(
                HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Attendance already recorded for this date");

        ResponseEntity<ApiError> response = handler.handleApiException(exception, request("/api/attendance"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Attendance already recorded for this date");
        assertThat(response.getBody().path()).isEqualTo("/api/attendance");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void validationFailuresAreReportedPerField() throws Exception {
        Method method = SampleHandler.class.getDeclaredMethod("handle", SampleRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new SampleRequest(), "sampleRequest");
        bindingResult.addError(new FieldError("sampleRequest", "username", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiError> response = handler.handleMethodArgumentNotValid(exception, request("/api/users"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().fieldErrors())
                .containsExactly(new FieldViolation("username", "must not be blank"));
    }

    @Test
    void unexpectedFailuresDoNotLeakInternalDetails() throws Exception {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://localhost:5433/school"), request("/api/users"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("jdbc");
    }

    @Test
    void accessDeniedIsRethrownSoSpringSecurityRendersTheForbiddenPayload() {
        HttpServletRequest request = request("/api/students");

        assertThatThrownBy(() -> handler.handleUnexpected(new AccessDeniedException("denied"), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    private HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    static class SampleHandler {

        public void handle(SampleRequest request) {
        }
    }

    static class SampleRequest {

        private String username;
    }
}
