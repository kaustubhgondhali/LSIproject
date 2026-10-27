package com.lordsai.lsi.dto.slider;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SliderDtos {

    private SliderDtos() {
    }

    /** Metadata fields for creating/updating a slider image. The image file itself travels
     *  alongside this as a separate multipart part (create) or through the dedicated
     *  replace-image endpoint (update). */
    public record SliderImageRequest(
            @Size(max = 200, message = "Title is too long.") String title,
            @Size(max = 300, message = "Subtitle is too long.") String subtitle,
            @Size(max = 100, message = "Button text is too long.") String buttonText,
            @Size(max = 500, message = "Button link is too long.") String buttonLink,
            @Size(max = 255, message = "Alt text is too long.") String altText,
            Integer displayOrder
    ) {
    }

    /** What the public homepage slider actually needs — no admin bookkeeping fields. */
    public record PublicSlide(
            Long id,
            String imagePath,
            String title,
            String subtitle,
            String buttonText,
            String buttonLink,
            String altText
    ) {
    }

    public record AdminSlide(
            Long id,
            String imagePath,
            String title,
            String subtitle,
            String buttonText,
            String buttonLink,
            String altText,
            int displayOrder,
            boolean active,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
