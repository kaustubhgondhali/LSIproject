package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local-disk storage for lesson videos, materials and images. Only relative paths are kept in the
 * database, and every path is resolved back under the configured base directory so a stored
 * value can never escape it.
 */
@Service
public class FileStorageService {

    public enum Kind {
        VIDEO("videos", Set.of("mp4", "webm", "m4v", "mov"), Set.of("video/mp4", "video/webm", "video/quicktime", "video/x-m4v")),
        DOCUMENT("pdfs", Set.of("pdf"), Set.of("application/pdf")),
        IMAGE("images", Set.of("jpg", "jpeg", "png", "webp"), Set.of("image/jpeg", "image/png", "image/webp")),
        ATTACHMENT("attachments", Set.of("pdf", "jpg", "jpeg", "png", "webp"),
                Set.of("application/pdf", "image/jpeg", "image/png", "image/webp"));

        final String folder;
        final Set<String> extensions;
        final Set<String> contentTypes;

        Kind(String folder, Set<String> extensions, Set<String> contentTypes) {
            this.folder = folder;
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }
    }

    private final AppProperties properties;
    private Path basePath;

    public FileStorageService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws IOException {
        basePath = Paths.get(properties.storage().basePath()).toAbsolutePath().normalize();
        for (Kind kind : Kind.values()) {
            Files.createDirectories(basePath.resolve(kind.folder));
        }
    }

    /** Validates type and size, stores under a random name, returns the relative path to persist. */
    public String store(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!kind.extensions.contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Allowed: " + String.join(", ", kind.extensions));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!kind.contentTypes.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file does not look like a valid " + kind.name().toLowerCase() + ".");
        }
        long maxBytes = maxBytesFor(kind);
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "File is too large. Maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }

        String relative = kind.folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the uploaded file.");
        }
        return relative;
    }

    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        }
        try {
            return new UrlResource(path.toUri());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        }
    }

    public Path resolvePath(String relativePath) {
        return resolve(relativePath);
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | ApiException ignored) {
            // Best effort; a stale file on disk is harmless.
        }
    }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        }
        Path resolved = basePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file path.");
        }
        return resolved;
    }

    private long maxBytesFor(Kind kind) {
        long mb = switch (kind) {
            case VIDEO -> properties.storage().maxVideoSizeMb();
            case DOCUMENT, ATTACHMENT -> properties.storage().maxDocumentSizeMb();
            case IMAGE -> 5;
        };
        return mb * 1024 * 1024;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
