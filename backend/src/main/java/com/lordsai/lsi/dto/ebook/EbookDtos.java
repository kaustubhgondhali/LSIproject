package com.lordsai.lsi.dto.ebook;

import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class EbookDtos {

    private EbookDtos() {
    }

    // ---- Requests --------------------------------------------------------------------------

    public record EbookRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*$", message = "Ebook code may contain letters, numbers and hyphens only.")
            String ebookCode,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 150) String author,
            @Size(max = 500) String shortDescription,
            @Size(max = 10000) String description,
            @Size(max = 100) String category,
            @Size(max = 60) String language,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal discountedPrice,
            Integer displayOrder
    ) {
    }

    public record EbookPriceRequest(
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal discountedPrice
    ) {
    }

    public record EbookStatusRequest(@NotNull EbookStatus status) {
    }

    public record GrantEbookRequest(@NotNull Long studentUserId, @NotNull Long ebookId) {
    }

    // ---- Responses -------------------------------------------------------------------------

    /** What visitors see on the store page. Never includes the PDF path. */
    public record PublicEbook(
            Long id,
            String ebookCode,
            String title,
            String author,
            String shortDescription,
            String description,
            String category,
            String language,
            BigDecimal price,
            BigDecimal discountedPrice,
            BigDecimal effectivePrice,
            String coverImagePath,
            boolean available,
            boolean hasPdf
    ) {
    }

    /** Admin view with counts, status and file metadata (still no file path for the PDF). */
    public record AdminEbook(
            Long id,
            String ebookCode,
            String title,
            String author,
            String shortDescription,
            String description,
            String category,
            String language,
            BigDecimal price,
            BigDecimal discountedPrice,
            BigDecimal effectivePrice,
            String coverImagePath,
            boolean hasPdf,
            String pdfOriginalName,
            Long pdfSizeBytes,
            EbookStatus status,
            int displayOrder,
            long purchaseCount,
            long activeReaders,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** A student's ebook in "My Ebooks". */
    public record MyEbook(
            Long entitlementId,
            Long ebookId,
            String ebookCode,
            String title,
            String author,
            String shortDescription,
            String category,
            String language,
            String coverImagePath,
            EntitlementStatus status,
            Instant purchasedAt,
            Long invoiceId,
            String invoiceNumber,
            boolean fileAvailable
    ) {
    }

    /** Admin listing of who owns an ebook / what a student owns. */
    public record EntitlementResponse(
            Long id,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            Long ebookId,
            String ebookCode,
            String title,
            EntitlementStatus status,
            String source,
            Long paymentId,
            String orderRef,
            Instant grantedAt
    ) {
    }
}
