package com.school.notice.web;

import com.school.notice.service.NoticeAttachmentDownload;
import com.school.notice.service.NoticeService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Public download endpoint for notice attachments. It is reachable without a session because the
 * signed URL itself is the grant: the {@code signature} is an HMAC that also carries an expiry.
 */
@RestController
public class NoticeAttachmentController {

    private final NoticeService noticeService;

    public NoticeAttachmentController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/notices/attachments/{noticeId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long noticeId,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        NoticeAttachmentDownload download = noticeService.downloadAttachment(noticeId, expires, signature);

        MediaType mediaType = download.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(download.contentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }
}
