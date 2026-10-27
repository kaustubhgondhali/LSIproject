package com.lordsai.lsi.dto.invoice;

import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** Invoice row for the student "My Invoices" list and the admin invoice screens. */
    public record InvoiceResponse(
            Long id,
            String invoiceNumber,
            Long paymentId,
            String orderRef,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            String studentPhone,
            ProductType productType,
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            String currency,
            String paymentMethod,
            String transactionId,
            PaymentStatus paymentStatus,
            LocalDate invoiceDate,
            Instant purchaseDate,
            boolean pdfAvailable,
            Instant emailedAt,
            int emailCount,
            Instant createdAt
    ) {
    }
}
