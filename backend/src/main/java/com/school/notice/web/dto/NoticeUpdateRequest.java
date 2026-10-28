package com.school.notice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Edit a notice's content. {@code expiresAt} sets a new expiry; {@code clearExpiry} removes it;
 * {@code removeAttachment} deletes any attachment.
 */
public record NoticeUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 20000) String body,
        OffsetDateTime expiresAt,
        Boolean clearExpiry,
        Boolean removeAttachment
) {
}
