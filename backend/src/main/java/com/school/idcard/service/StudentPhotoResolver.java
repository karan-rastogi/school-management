package com.school.idcard.service;

import com.school.idcard.config.IdCardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Resolves a {@link com.school.student.domain.Student#getPhotoPath()} value to image bytes for the
 * ID card. Only local filesystem paths (absolute, or relative to {@code app.id-cards.photo-directory})
 * and {@code classpath:} resources are read; remote URLs are deliberately not fetched so a stored
 * path can never be turned into a server-side request forgery. Anything unreadable yields an empty
 * result and the renderer falls back to an initials placeholder.
 */
@Component
public class StudentPhotoResolver {

    private static final Logger log = LoggerFactory.getLogger(StudentPhotoResolver.class);
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final IdCardProperties properties;

    public StudentPhotoResolver(IdCardProperties properties) {
        this.properties = properties;
    }

    public Optional<byte[]> resolve(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) {
            return Optional.empty();
        }
        String path = photoPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            log.debug("Skipping remote student photo path for the ID card.");
            return Optional.empty();
        }
        if (path.startsWith("classpath:")) {
            return readClasspath(path.substring("classpath:".length()));
        }
        return readFile(path);
    }

    private Optional<byte[]> readClasspath(String location) {
        String normalised = location.startsWith("/") ? location.substring(1) : location;
        ClassPathResource resource = new ClassPathResource(normalised);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(readLimited(in));
        } catch (IOException exception) {
            log.debug("Could not read classpath student photo for the ID card.");
            return Optional.empty();
        }
    }

    private Optional<byte[]> readFile(String path) {
        Path resolved = Paths.get(path);
        if (!resolved.isAbsolute()) {
            resolved = Paths.get(properties.photoDirectory()).resolve(path).normalize();
        }
        if (!Files.isReadable(resolved) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        try {
            if (Files.size(resolved) > MAX_PHOTO_BYTES) {
                log.debug("Student photo is larger than the {} byte limit; using the placeholder.", MAX_PHOTO_BYTES);
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(resolved));
        } catch (IOException exception) {
            log.debug("Could not read student photo for the ID card.");
            return Optional.empty();
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes((int) MAX_PHOTO_BYTES + 1);
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new IOException("Photo exceeds the size limit.");
        }
        return bytes;
    }
}
