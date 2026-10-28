package com.school.notice.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoticePropertiesTest {

    private final NoticeProperties properties = new NoticeProperties(
            Duration.ofMinutes(15),
            2048,
            List.of("application/pdf", "image/png"),
            "build/tmp/notice-attachments",
            "test-signing-secret-0123456789-abcdefghij");

    @Test
    void allowedTypesAreMatchedCaseInsensitivelyAndIgnoreParameters() {
        assertThat(properties.contentTypeAllowed("application/pdf")).isTrue();
        assertThat(properties.contentTypeAllowed("APPLICATION/PDF")).isTrue();
        assertThat(properties.contentTypeAllowed("text/plain;charset=UTF-8")).isFalse();
        assertThat(properties.contentTypeAllowed("application/zip")).isFalse();
        assertThat(properties.contentTypeAllowed(null)).isFalse();
    }
}
