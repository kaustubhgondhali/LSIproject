package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.StoryCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-managed video story for the public Success Stories page. The video is either a
 * file uploaded through the admin panel (videoPath, streamed by the public API) or an
 * external embed URL (videoUrl). Only published stories are ever returned publicly.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "success_stories")
public class SuccessStory extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoryCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "person_name", length = 150)
    private String personName;

    @Column(length = 150)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_path", length = 255)
    private String thumbnailPath;

    @Column(name = "video_path", length = 255)
    private String videoPath;

    @Column(name = "video_original_name", length = 255)
    private String videoOriginalName;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    public boolean hasVideo() {
        return (videoPath != null && !videoPath.isBlank()) || (videoUrl != null && !videoUrl.isBlank());
    }
}
