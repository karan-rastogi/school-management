package com.school.notice.web.dto;

import java.time.OffsetDateTime;

/** Attachment metadata plus a short-lived signed URL the client can fetch without a session. */
public record NoticeAttachmentResponse(
        String fileName,
        String contentType,
        long sizeBytes,
        OffsetDateTime uploadedAt,
        String url
) {
}
