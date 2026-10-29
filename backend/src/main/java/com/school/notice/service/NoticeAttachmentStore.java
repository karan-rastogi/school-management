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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores notice attachments on the local filesystem under {@code app.notices.storage-directory}.
 * Files are keyed by a random UUID and accessed only through signed URLs.
 *
 * <p>Uploads are limited by size and by an allow-list of content types, and the declared content
 * type must also match the file's leading bytes for the types that have a known signature (PDF,
 * PNG, JPEG, WebP), so renaming a file cannot smuggle it past the type allow-list.
 */
@Component
public class NoticeAttachmentStore {

    private static final int SNIFF_BYTES = 16;

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
        requireSignatureMatchesDeclaredType(file);

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

    private void requireSignatureMatchesDeclaredType(MultipartFile file) {
        String baseType = baseContentType(file.getContentType());
        byte[] header = readHeader(file);
        if (!signatureMatches(baseType, header)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The file contents do not match the declared type (" + baseType + ").");
        }
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The attachment could not be read.");
        }
    }

    /**
     * Whether the file's leading bytes are consistent with {@code baseType}. Types without a known
     * signature (for example {@code text/plain}) are not constrained — the prefix check only adds
     * a check; it never loosens the size and content-type allow-list.
     */
    private static boolean signatureMatches(String baseType, byte[] header) {
        return switch (baseType) {
            case "application/pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.ISO_8859_1));
            case "image/png" -> startsWith(header, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/webp" -> startsWith(header, "RIFF".getBytes(StandardCharsets.ISO_8859_1))
                    && matchesAt(header, 8, "WEBP".getBytes(StandardCharsets.ISO_8859_1));
            default -> true;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return matchesAt(data, 0, prefix);
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (data[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static String baseContentType(String contentType) {
        return contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
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
