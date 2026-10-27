package com.lordsai.lsi.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.service.AcadPaymentService;
import com.lordsai.lsi.automation.service.AcadReceiptPdfService;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Opens the document behind a private link from a WhatsApp message (see {@link DocumentLinkService}):
 * a payment receipt, an invoice or a message attachment — no login, but only with a valid, unexpired link.
 */
@Service
public class SharedDocumentService {

    public static final String INVALID = "This link is not valid. Please make sure the whole link was opened.";
    public static final String EXPIRED = "This link has expired. Please ask the academy to send it again.";

    /** What the public page shows before the download. Never the student's personal details. */
    public record Info(String kind, String title, String number, LocalDate date, LocalDate expiresOn, String fileName) {
    }

    public record SharedFile(String fileName, String contentType, byte[] content) {
    }

    private final DocumentLinkService links;
    private final AcadReceiptRepository receipts;
    private final AcadPaymentService paymentService;
    private final AcadReceiptPdfService receiptPdf;
    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;
    private final FileStorageService storage;

    public SharedDocumentService(DocumentLinkService links, AcadReceiptRepository receipts, AcadPaymentService paymentService,
                                 AcadReceiptPdfService receiptPdf, InvoiceRepository invoices, InvoiceService invoiceService,
                                 FileStorageService storage) {
        this.links = links;
        this.receipts = receipts;
        this.paymentService = paymentService;
        this.receiptPdf = receiptPdf;
        this.invoices = invoices;
        this.invoiceService = invoiceService;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Info describe(String token) {
        DocumentLinkService.Target t = require(token);
        return switch (t.kind()) {
            case RECEIPT -> {
                ReceiptDto r = paymentService.toDto(receipt(t));
                yield new Info("RECEIPT", "Payment receipt", r.receiptNo(), r.paymentDate(), t.expiresOn(), AcadReceiptPdfService.fileName(r));
            }
            case INVOICE -> {
                Invoice i = invoice(t);
                yield new Info("INVOICE", "Invoice", i.getInvoiceNumber(), i.getInvoiceDate(), t.expiresOn(), i.fileName());
            }
            case ATTACHMENT -> new Info("ATTACHMENT", "Attachment", null, null, t.expiresOn(), attachmentName(attachment(t)));
        };
    }

    /** Not read-only: an invoice whose stored PDF must be re-rendered is saved again (see InvoiceService.pdfBytes). */
    @Transactional
    public SharedFile open(String token) {
        DocumentLinkService.Target t = require(token);
        return switch (t.kind()) {
            case RECEIPT -> {
                AcadReceiptPdfService.ReceiptFile f = receiptPdf.render(receipt(t));
                yield new SharedFile(f.filename(), "application/pdf", f.content());
            }
            case INVOICE -> {
                Invoice i = invoice(t);
                yield new SharedFile(i.fileName(), "application/pdf", invoiceService.pdfBytes(i));
            }
            case ATTACHMENT -> {
                String path = attachment(t);
                yield new SharedFile(attachmentName(path), contentType(path), storage.readBytes(path));
            }
        };
    }

    private DocumentLinkService.Target require(String token) {
        DocumentLinkService.Verified v = links.verify(token);
        if (v.check() == DocumentLinkService.Check.INVALID) {
            throw new ApiException(HttpStatus.NOT_FOUND, INVALID);
        }
        if (v.check() == DocumentLinkService.Check.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, EXPIRED);
        }
        return v.target();
    }

    private AcadReceipt receipt(DocumentLinkService.Target t) {
        return receipts.findById(t.id()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "This receipt is no longer available. Please contact the academy."));
    }

    private Invoice invoice(DocumentLinkService.Target t) {
        return invoices.findById(t.id()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "This invoice is no longer available. Please contact the academy."));
    }

    /** Only office message attachments can be shared this way, never other stored files. */
    private String attachment(DocumentLinkService.Target t) {
        String path = t.ref();
        if (!path.startsWith("attachments/") || path.contains("..") || !storage.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This attachment is no longer available. Please contact the academy.");
        }
        return path;
    }

    private static String attachmentName(String path) {
        String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "pdf";
        return "Lord-Sai-Attachment." + ext;
    }

    private static String contentType(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
