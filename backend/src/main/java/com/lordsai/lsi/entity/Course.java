package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_courses_code", columnList = "course_code", unique = true),
        @Index(name = "idx_courses_status", columnList = "status")
})
public class Course extends BaseEntity {

    @Column(name = "course_code", nullable = false, length = 40)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Original / list price in INR. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** The price actually charged. If null, {@link #price} is charged. */
    @Column(name = "discounted_price", precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    @Column(length = 100)
    private String duration;

    @Column(name = "thumbnail_path", length = 255)
    private String thumbnailPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** The amount the backend charges. Never derived from anything the frontend sends. */
    public BigDecimal effectivePrice() {
        return discountedPrice != null ? discountedPrice : price;
    }
}
