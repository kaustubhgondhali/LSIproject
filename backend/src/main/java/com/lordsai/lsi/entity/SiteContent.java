package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single admin-editable piece of website copy, keyed like "home.hero.title".
 * Only content that benefits from admin editing is stored here — static markup stays static.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "site_content",
        uniqueConstraints = @UniqueConstraint(name = "uk_site_content_key",
                columnNames = {"site_id", "content_key"}))
public class SiteContent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "content_key", nullable = false, length = 150)
    private String contentKey;

    /** Human label shown in the admin editor, e.g. "Home page hero heading". */
    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "content_value", columnDefinition = "TEXT")
    private String contentValue;

    /** TEXT, HTML, IMAGE_PATH or URL — tells the admin editor which input to render. */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "TEXT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
