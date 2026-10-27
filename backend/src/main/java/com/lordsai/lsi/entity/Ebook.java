package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.EbookStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A downloadable ebook sold through the same Razorpay pipeline as courses. The cover is a public
 * image (served like course thumbnails); the PDF lives under the protected "ebooks/" storage
 * folder and is only ever streamed through the authenticated, entitlement-checked student endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ebooks", indexes = {
        @Index(name = "idx_ebooks_code", columnList = "ebook_code", unique = true),
        @Index(name = "idx_ebooks_status", columnList = "status, display_order")
})
public class Ebook extends BaseEntity {

    @Column(name = "ebook_code", nullable = false, length = 40)
    private String ebookCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 150)
    private String author;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 60)
    private String language;

    /** List price in INR. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** The price actually charged when set. */
    @Column(name = "discounted_price", precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "cover_image_path", length = 255)
    private String coverImagePath;

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "pdf_original_name", length = 255)
    private String pdfOriginalName;

    @Column(name = "pdf_size_bytes")
    private Long pdfSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EbookStatus status = EbookStatus.DRAFT;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    /** The amount the backend charges. Never derived from anything the frontend sends. */
    public BigDecimal effectivePrice() {
        return discountedPrice != null ? discountedPrice : price;
    }
}
