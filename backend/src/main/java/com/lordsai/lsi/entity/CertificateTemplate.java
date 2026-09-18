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

/**
 * An admin-uploaded certificate background (A4 landscape image). At most one row is active; when
 * none is, the built-in default Lord Sai certificate is used. The default is a classpath template,
 * not a row here, so it can never be deleted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificate_templates", indexes = @Index(name = "idx_cert_templates_active", columnList = "active"))
public class CertificateTemplate extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    /** certificate-templates/<uuid>.<ext> — served only through authenticated endpoints. */
    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "content_type", length = 60)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false)
    private boolean active = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;
}
