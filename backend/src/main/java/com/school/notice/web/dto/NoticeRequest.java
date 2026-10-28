package com.school.notice.web.dto;

import com.school.notice.domain.NoticeScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Create a notice. {@code scope} must be GLOBAL (admins) or CLASS (with a {@code classId}).
 * {@code senderLabel} is only honoured for admins; a teacher's label is derived from their
 * assignment at send time.
 */
public record NoticeRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 20000) String body,
        @NotNull NoticeScope scope,
        Long classId,
        OffsetDateTime expiresAt,
        @Size(max = 100) String senderLabel
) {
}
