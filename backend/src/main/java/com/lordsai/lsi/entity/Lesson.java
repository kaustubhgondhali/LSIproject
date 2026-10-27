package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lessons_module", columnList = "module_id, display_order")
})
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(name = "lesson_title", nullable = false, length = 200)
    private String lessonTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Relative path under the configured storage directory. Never exposed directly to the
     * browser; videos are streamed through an endpoint that checks enrollment first.
     */
    @Column(name = "video_path", length = 255)
    private String videoPath;

    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Column(name = "material_path", length = 255)
    private String materialPath;

    @Column(name = "material_original_name", length = 255)
    private String materialOriginalName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
