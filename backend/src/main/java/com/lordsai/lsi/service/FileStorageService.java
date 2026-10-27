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
                Set.of("application/pdf", "image/jpeg", "image/png", "image/webp")),
        /** Purchased ebook files. Never exposed by a public endpoint; streamed only after the entitlement check. */
        EBOOK("ebooks", Set.of("pdf"), Set.of("application/pdf")),
        /** Server-generated invoice PDFs. Served only to the owning student or an admin. */
        INVOICE("invoices", Set.of("pdf"), Set.of("application/pdf")),
        /** Server-generated course certificates. Served only to the owning student or an admin. */
        CERTIFICATE("certificates", Set.of("pdf"), Set.of("application/pdf")),
        /** Admin-uploaded certificate background designs (JPG / PNG only — the PDF renderer embeds them). */
        CERT_TEMPLATE("certificate-templates", Set.of("jpg", "jpeg", "png"), Set.of("image/jpeg", "image/png"));

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

    /**
     * Cheap content sniff so a renamed file cannot pass as a PDF or image: %PDF- for PDFs,
     * JPEG / PNG / WebP signatures for images. Kinds without a signature check return true.
     */
    public static boolean looksLike(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        byte[] head;
        try (java.io.InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        return switch (kind) {
            case EBOOK, DOCUMENT, INVOICE, CERTIFICATE -> startsWith(head, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case IMAGE, CERT_TEMPLATE -> isImage(head);
            case ATTACHMENT -> startsWith(head, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) || isImage(head);
            case VIDEO -> true;
        };
    }

    private static boolean isImage(byte[] head) {
        return startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8})                                   // JPEG
                || startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G'})                              // PNG
                || (head.length >= 12 && startsWith(head, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P');           // WebP
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Type (extension + declared content type) and size checks, without writing anything. */
    public void validate(MultipartFile file, Kind kind) {
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
    }

    /** Validates type and size, stores under a random name, returns the relative path to persist. */
    public String store(MultipartFile file, Kind kind) {
        validate(file, kind);
        String extension = extensionOf(file.getOriginalFilename());
        String relative = kind.folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the uploaded file.");
        }
        return relative;
    }

    /**
     * Persists server-generated bytes (e.g. an invoice PDF) under a random name in the given
     * folder and returns the relative path to store. Same containment guarantees as {@link #store}.
     */
    public String storeBytes(byte[] content, Kind kind, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT).replace(".", "");
        if (!kind.extensions.contains(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported file type for " + kind.name().toLowerCase() + ".");
        }
        String relative = kind.folder + "/" + UUID.randomUUID() + "." + ext;
        try {
            Files.write(resolve(relative), content);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the generated file.");
        }
        return relative;
    }

    public byte[] readBytes(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        }
    }

    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(resolve(relativePath));
        } catch (ApiException e) {
            return false;
        }
    }

    /** Strips directories and unsafe characters from a client-supplied filename before it is stored or echoed. */
    public static String safeFilename(String original, String fallback) {
        if (original == null || original.isBlank()) {
            return fallback;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isBlank() || name.startsWith(".")) {
            return fallback;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
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
            case DOCUMENT, ATTACHMENT, INVOICE, CERTIFICATE -> properties.storage().maxDocumentSizeMb();
            case EBOOK -> properties.storage().ebookLimitMb();
            case IMAGE -> 5;
            case CERT_TEMPLATE -> 10;
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
