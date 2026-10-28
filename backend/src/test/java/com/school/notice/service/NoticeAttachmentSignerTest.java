package com.school.notice.service;

import com.school.common.error.ApiException;
import com.school.notice.config.NoticeProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeAttachmentSignerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private final NoticeAttachmentSigner signer = new NoticeAttachmentSigner(
            new NoticeProperties(
                    Duration.ofMinutes(15),
                    1024,
                    List.of("application/pdf"),
                    "build/tmp/notice-attachments",
                    "test-signing-secret-0123456789-abcdefghij"),
            clock);

    @Test
    void aSignatureFromTheSignedUrlVerifies() {
        long expires = Instant.now(clock).plusSeconds(600).getEpochSecond();
        String signature = signer.signature(7L, expires);

        assertThatCode(() -> signer.verify(7L, expires, signature)).doesNotThrowAnyException();
        assertThat(signer.signedUrl(7L)).contains("/notices/attachments/7?expires=");
    }

    @Test
    void aTamperedSignatureIsRejected() {
        long expires = Instant.now(clock).plusSeconds(600).getEpochSecond();
        String signature = signer.signature(7L, expires);

        assertThatThrownBy(() -> signer.verify(7L, expires, signature + "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void aSignatureForAnotherNoticeIsRejected() {
        long expires = Instant.now(clock).plusSeconds(600).getEpochSecond();
        String signature = signer.signature(7L, expires);

        assertThatThrownBy(() -> signer.verify(8L, expires, signature))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void anExpiredSignatureIsRejected() {
        long expired = Instant.now(clock).minusSeconds(1).getEpochSecond();

        assertThatThrownBy(() -> signer.verify(7L, expired, "whatever"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or has expired");
    }
}
