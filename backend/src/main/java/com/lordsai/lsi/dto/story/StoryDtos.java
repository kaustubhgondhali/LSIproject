package com.lordsai.lsi.dto.story;

import com.lordsai.lsi.entity.enums.StoryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class StoryDtos {

    private StoryDtos() {
    }

    public record StoryRequest(
            @NotNull StoryCategory category,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 150) String personName,
            @Size(max = 150) String location,
            @Size(max = 5000) String description,
            /** Optional external embed URL; only https YouTube embeds are accepted to keep the public page safe. */
            @Size(max = 500)
            @Pattern(regexp = "^$|^https://(www[.])?(youtube[.]com|youtube-nocookie[.]com)/embed/[A-Za-z0-9_-]+([?][A-Za-z0-9_=&-]*)?$",
                    message = "Video URL must be a YouTube embed link (https://www.youtube.com/embed/...) or left blank.")
            String videoUrl,
            Integer displayOrder
    ) {
    }

    public record PublishRequest(boolean published) {
    }

    /** Public shape: never includes storage paths or who edited it. */
    public record PublicStory(
            Long id,
            StoryCategory category,
            String title,
            String personName,
            String location,
            String description,
            /** Static site path (img/...) or an admin upload (images/<file>, served by /api/public/images/<file>). */
            String thumbnailPath,
            /** UPLOAD -> stream GET /api/public/stories/{id}/video; EMBED -> iframe videoUrl; NONE -> no video yet. */
            String videoType,
            String videoUrl
    ) {
    }

    public record AdminStory(
            Long id,
            StoryCategory category,
            String title,
            String personName,
            String location,
            String description,
            String thumbnailPath,
            boolean hasUploadedVideo,
            String videoOriginalName,
            String videoUrl,
            boolean published,
            int displayOrder,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
