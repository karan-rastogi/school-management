package com.school.notice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Notice attachment and signed-URL settings, all in one place under {@code app.notices}.
 */
@Validated
@ConfigurationProperties(prefix = "app.notices")
public record NoticeProperties(
        @DefaultValue("PT15M") Duration signedUrlTtl,
        @Positive @DefaultValue("10485760") long maxAttachmentBytes,
        @DefaultValue({"application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain"})
        List<String> allowedContentTypes,
        @DefaultValue("./uploads/notice-attachments") String storageDirectory,
        @NotBlank @DefaultValue("dev-only-notice-signing-secret-change-me-0123456789") String signingSecret
) {

    /** Whether the (possibly parameterised) content type is one of the configured allowed types. */
    public boolean contentTypeAllowed(String contentType) {
        if (contentType == null) {
            return false;
        }
        String base = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return allowedContentTypes.stream()
                .map(allowed -> allowed.trim().toLowerCase(Locale.ROOT))
                .anyMatch(base::equals);
    }

    public String allowedContentTypesLabel() {
        return String.join(", ", allowedContentTypes);
    }
}
