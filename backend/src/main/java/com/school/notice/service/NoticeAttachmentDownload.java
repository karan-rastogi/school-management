package com.school.notice.service;

import org.springframework.core.io.Resource;

/** A verified attachment ready to be streamed back to the client. */
public record NoticeAttachmentDownload(
        Resource resource,
        String fileName,
        String contentType,
        long sizeBytes
) {
}
