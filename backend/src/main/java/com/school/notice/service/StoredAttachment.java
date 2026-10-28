package com.school.notice.service;

/** The metadata produced when an attachment is written to storage. */
public record StoredAttachment(
        String key,
        String fileName,
        String contentType,
        long sizeBytes
) {
}
