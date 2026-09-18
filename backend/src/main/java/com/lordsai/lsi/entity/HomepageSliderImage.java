package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One image in an admin-managed homepage hero slider. Currently used only by the Mutual Fund
 * website's existing carousel (home.html #mfCarouselTrack); the Share Market homepage slider
 * is untouched and does not use this table.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "homepage_slider_images")
public class HomepageSliderImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    @Column(length = 200)
    private String title;

    @Column(length = 300)
    private String subtitle;

    @Column(name = "button_text", length = 100)
    private String buttonText;

    @Column(name = "button_link", length = 500)
    private String buttonLink;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
