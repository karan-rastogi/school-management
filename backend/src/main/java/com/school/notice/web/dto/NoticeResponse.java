package com.school.notice.web.dto;

import com.school.notice.domain.NoticeScope;

import java.time.OffsetDateTime;

/**
 * A notice as returned to staff and parents. {@code childName} is set only on the parent feed for a
 * class notice, so a parent can tell which child it concerns.
 */
public record NoticeResponse(
        Long id,
        String title,
        String body,
        NoticeScope scope,
        Long classId,
        String className,
        String section,
        String classLabel,
        Long senderUserId,
        String senderName,
        String senderLabel,
        OffsetDateTime createdAt,
        OffsetDateTime editedAt,
        boolean edited,
        OffsetDateTime expiresAt,
        boolean expired,
        String childName,
        NoticeAttachmentResponse attachment
) {
}
