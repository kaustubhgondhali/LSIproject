package com.lordsai.lsi.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

/**
 * HTTP Range streaming shared by the protected lesson player and the public success-story
 * player. Callers perform their own authorization before handing the resource in here.
 */
public final class VideoStreaming {

    private static final long CHUNK_SIZE = 2L * 1024 * 1024;

    private VideoStreaming() {
    }

    public static ResponseEntity<ResourceRegion> partial(Resource video, String rangeHeader, String cacheControl)
            throws IOException {
        long length = video.contentLength();
        MediaType type = MediaTypeFactory.getMediaType(video).orElse(MediaType.APPLICATION_OCTET_STREAM);

        ResourceRegion region;
        if (rangeHeader == null || rangeHeader.isBlank()) {
            region = new ResourceRegion(video, 0, Math.min(CHUNK_SIZE, length));
        } else {
            HttpRange range = HttpRange.parseRanges(rangeHeader).get(0);
            long start = range.getRangeStart(length);
            long end = range.getRangeEnd(length);
            region = new ResourceRegion(video, start, Math.min(CHUNK_SIZE, end - start + 1));
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(type)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .header("X-Content-Type-Options", "nosniff")
                .body(region);
    }
}
