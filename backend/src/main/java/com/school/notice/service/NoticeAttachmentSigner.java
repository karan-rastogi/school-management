package com.school.notice.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.notice.config.NoticeProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Produces and verifies short-lived signed URLs for notice attachments. The signature is an
 * HMAC-SHA256 over {@code noticeId:expiresEpochSecond}; the download endpoint is public so the URL
 * itself is the grant, and it expires.
 */
@Component
public class NoticeAttachmentSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;

    public NoticeAttachmentSigner(NoticeProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.signingSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.ttl = properties.signedUrlTtl();
        this.clock = clock;
    }

    public String signedUrl(Long noticeId) {
        long expires = Instant.now(clock).plus(ttl).getEpochSecond();
        return "/notices/attachments/" + noticeId
                + "?expires=" + expires
                + "&signature=" + signature(noticeId, expires);
    }

    public void verify(Long noticeId, long expires, String signature) {
        if (signature == null || Instant.now(clock).getEpochSecond() > expires) {
            throw forbidden();
        }
        byte[] expected = signature(noticeId, expires).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw forbidden();
        }
    }

    String signature(Long noticeId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((noticeId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign the attachment URL.", exception);
        }
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "This attachment link is invalid or has expired.");
    }
}
