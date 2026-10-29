package com.school.idcard.service;

import com.school.idcard.config.IdCardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentPhotoResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAPhotoRelativeToTheConfiguredDirectory() throws Exception {
        Files.write(tempDir.resolve("aarav.png"), new byte[] {1, 2, 3});
        StudentPhotoResolver resolver = new StudentPhotoResolver(properties(tempDir));

        assertThat(resolver.resolve("aarav.png")).isPresent();
        assertThat(resolver.resolve("aarav.png").orElseThrow()).isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void readsAnAbsolutePhotoPath() throws Exception {
        Path absolute = tempDir.resolve("absolute.png");
        Files.write(absolute, new byte[] {9, 8, 7});
        StudentPhotoResolver resolver = new StudentPhotoResolver(properties(tempDir));

        assertThat(resolver.resolve(absolute.toString()).orElseThrow()).isEqualTo(new byte[] {9, 8, 7});
    }

    @Test
    void returnsEmptyForBlankMissingOrRemotePaths() {
        StudentPhotoResolver resolver = new StudentPhotoResolver(properties(tempDir));

        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
        assertThat(resolver.resolve("missing.png")).isEmpty();
        assertThat(resolver.resolve("https://example.com/photo.png")).isEmpty();
    }

    private static IdCardProperties properties(Path directory) {
        return new IdCardProperties("School", directory.toString(), 85.6f, 54f, 2, 4, 12f, 8f);
    }
}
