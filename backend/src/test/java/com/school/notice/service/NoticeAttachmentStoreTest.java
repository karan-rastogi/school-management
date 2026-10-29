package com.school.notice.service;

import com.school.common.error.ApiException;
import com.school.notice.config.NoticeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class NoticeAttachmentStoreTest {

    private NoticeAttachmentStore store(Path directory, long maxBytes, List<String> allowed) {
        return new NoticeAttachmentStore(new NoticeProperties(
                Duration.ofMinutes(15),
                maxBytes,
                allowed,
                directory.toString(),
                "test-signing-secret-0123456789-abcdefghij"));
    }

    @Test
    void storesLoadsAndDeletesAnAllowedFile(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 1024, List.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "sports-day.pdf", "application/pdf", "%PDF-hello".getBytes(StandardCharsets.UTF_8));

        StoredAttachment stored = store.store(file);

        assertThat(stored.fileName()).isEqualTo("sports-day.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(stored.sizeBytes()).isEqualTo(10);
        assertThat(store.load(stored.key()).exists()).isTrue();

        store.delete(stored.key());
        assertThatThrownBy(() -> store.load(stored.key())).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAnOverSizedFile(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 4, List.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", "too big".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> store.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("byte limit");
    }

    @Test
    void rejectsADisallowedContentType(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 1024, List.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", "zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> store.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsAFileWhoseBytesDoNotMatchTheDeclaredType(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 1024, List.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-really.pdf", "application/pdf", "PK\u0003\u0004zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> store.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void rejectsAnEmptyFile(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 1024, List.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> store.store(file)).isInstanceOf(ApiException.class);
    }

    @Test
    void aTraversalKeyIsRejected(@TempDir Path directory) {
        NoticeAttachmentStore store = store(directory, 1024, List.of("application/pdf"));

        assertThatThrownBy(() -> store.load("../secrets")).isInstanceOf(ApiException.class);
        assertThatCode(() -> store.delete("../secrets")).doesNotThrowAnyException();
    }
}
