package com.school.notice.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.notice.config.NoticeProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores notice attachments on the local filesystem under {@code app.notices.storage-directory}.
 * Files are keyed by a random UUID and accessed only through signed URLs.
 */
@Component
public class NoticeAttachmentStore {

    private final NoticeProperties properties;
    private final Path root;

    public NoticeAttachmentStore(NoticeProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.storageDirectory()).toAbsolutePath().normalize();
    }

    public StoredAttachment store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The attachment is empty.");
        }
        if (file.getSize() > properties.maxAttachmentBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The attachment is larger than the " + properties.maxAttachmentBytes() + " byte limit.");
        }
        if (!properties.contentTypeAllowed(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "That file type is not allowed. Allowed types: " + properties.allowedContentTypesLabel() + ".");
        }

        String key = UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(key), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "The attachment could not be stored.");
        }
        return new StoredAttachment(key, sanitiseName(file.getOriginalFilename()), file.getContentType(), file.getSize());
    }

    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isReadable(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "The attachment is no longer available.");
        }
        return new FileSystemResource(path);
    }

    public void delete(String key) {
        if (key == null) {
            return;
        }
        Path path;
        try {
            UUID.fromString(key);
            path = root.resolve(key);
        } catch (IllegalArgumentException invalidKey) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A missing file is fine; the database row is the source of truth.
        }
    }

    private Path resolve(String key) {
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "The attachment is no longer available.");
        }
        return root.resolve(key);
    }

    private static String sanitiseName(String original) {
        if (original == null || original.isBlank()) {
            return "attachment";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return "attachment";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
