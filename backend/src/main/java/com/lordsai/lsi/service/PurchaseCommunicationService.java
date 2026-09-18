package com.lordsai.lsi.service;

import com.lordsai.lsi.communication.WhatsAppDelivery;
import com.lordsai.lsi.communication.WhatsAppMessageService;
import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.email.EmailAttachment;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.email.PurchaseEmail;
import com.lordsai.lsi.entity.CommunicationLog;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.repository.CommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the automatic purchase communications (course / ebook confirmation, "new purchase on your
 * existing account", invoice resend) with the invoice PDF attached, and records every attempt in
 * communication_logs. Nothing here ever throws for a delivery failure: the purchase, entitlement
 * and invoice stay successful and the admin can retry from the history screen.
 */
@Service
public class PurchaseCommunicationService {

    public static final String SUBJECT_COURSE = "Welcome to Lord Sai Share Market Academy — Your Purchase & Student Portal Details";
    public static final String SUBJECT_EBOOK = "Welcome to Lord Sai Share Market Academy — Your Purchase & Student Portal Details";
    public static final String SUBJECT_EXISTING = "New Purchase Added To Your Existing Student Account — Lord Sai Share Market Academy";
    public static final String ACCESS_COURSE = "Your purchased course is available only through the official Lord Sai Student Portal after login. Direct access to protected course content is not permitted.";
    public static final String ACCESS_EBOOK = "Your purchased ebook is available only inside your authenticated Lord Sai Student Portal account. Direct access to the protected ebook file is not permitted.";

    private static final Logger log = LoggerFactory.getLogger(PurchaseCommunicationService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final EmailService emailService;
    private final InvoiceService invoiceService;
    private final WhatsAppMessageService whatsApp;
    private final CommunicationLogRepository logRepository;
    private final AuditService auditService;
    private final AppProperties properties;

    public PurchaseCommunicationService(EmailService emailService,
                                        InvoiceService invoiceService,
                                        WhatsAppMessageService whatsApp,
                                        CommunicationLogRepository logRepository,
                                        AuditService auditService,
                                        AppProperties properties) {
        this.emailService = emailService;
        this.invoiceService = invoiceService;
        this.whatsApp = whatsApp;
        this.logRepository = logRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * The one purchase email. New accounts get the product-specific confirmation with the secure
     * setup link; existing accounts get "New Purchase Added To Your Existing Student Account".
     */
    @Transactional
    public EmailDelivery sendPurchaseConfirmation(Payment payment, User student, String studentCode, Invoice invoice,
                                                  boolean newAccount, String setupLink, String temporaryPassword) {
        CommunicationType kind = !newAccount ? CommunicationType.EXISTING_STUDENT_PURCHASE
                : payment.isEbook() ? CommunicationType.EBOOK_PURCHASE : CommunicationType.COURSE_PURCHASE;
        String subject = switch (kind) {
            case COURSE_PURCHASE -> SUBJECT_COURSE;
            case EBOOK_PURCHASE -> SUBJECT_EBOOK;
            default -> SUBJECT_EXISTING;
        };
        Map<String, Object> model = model(payment, student, studentCode, invoice);
        model.put("setupLink", setupLink);
        model.put("temporaryPassword", temporaryPassword);
        model.put("accessMessage", payment.isEbook() ? ACCESS_EBOOK : ACCESS_COURSE);

        EmailAttachment attachment = attachmentFor(invoice);
        EmailDelivery delivery = emailService.sendPurchaseEmail(new PurchaseEmail(kind, student.getEmail(), subject, model, attachment));
        record(kind, student, studentCode, student.getEmail(), subject, payment, invoice, delivery, null);
        return delivery;
    }

    /** Admin "Resend Email" on an invoice. Never includes credentials. */
    @Transactional
    public EmailDelivery resendInvoice(Invoice invoice, User actor, String ip) {
        Payment payment = invoice.getPayment();
        User student = invoice.getStudent();
        String subject = "Invoice " + invoice.getInvoiceNumber() + " — Lord Sai Share Market Academy";
        Map<String, Object> model = model(payment, student, invoice.getStudentCode(), invoice);
        EmailDelivery delivery = emailService.sendPurchaseEmail(new PurchaseEmail(CommunicationType.INVOICE,
                invoice.getStudentEmail(), subject, model, attachmentFor(invoice)));
        record(CommunicationType.INVOICE, student, invoice.getStudentCode(), invoice.getStudentEmail(), subject,
                payment, invoice, delivery, actor);
        auditService.record(actor, delivery.delivered() ? "INVOICE_RESENT" : "INVOICE_RESEND_FAILED", "Invoice", invoice.getId(),
                invoice.getInvoiceNumber() + " to " + invoice.getStudentEmail()
                        + (delivery.delivered() ? "" : " — " + delivery.status() + ": " + delivery.reason()), ip);
        return delivery;
    }

    /** Sends the invoice PDF as a WhatsApp document when the provider supports it. */
    @Transactional
    public WhatsAppDelivery sendInvoiceWhatsApp(Invoice invoice, User actor, String ip) {
        User student = invoice.getStudent();
        String mobile = invoice.getStudentPhone() != null ? invoice.getStudentPhone() : student.getMobile();
        String caption = "Lord Sai Share Market Academy — Invoice " + invoice.getInvoiceNumber() + " for "
                + invoice.getProductName() + " (" + InvoiceService.money(invoice.getTotal(), invoice.getCurrency()) + ").";
        WhatsAppDelivery delivery;
        if (mobile == null || mobile.isBlank()) {
            delivery = WhatsAppDelivery.failed("No mobile number on record for this student.", whatsApp.providerName());
        } else {
            byte[] pdf = invoiceService.pdfBytes(invoice);
            delivery = whatsApp.sendDocument(mobile, caption, invoice.fileName(), "application/pdf", pdf);
        }
        CommunicationLog row = new CommunicationLog();
        row.setChannel(CommunicationChannel.WHATSAPP);
        row.setMessageType(CommunicationType.INVOICE);
        row.setStudent(student);
        row.setStudentCode(invoice.getStudentCode());
        row.setRecipient(mobile == null ? "—" : mobile);
        row.setSubject("Invoice " + invoice.getInvoiceNumber());
        row.setBody(caption);
        row.setInvoice(invoice);
        row.setProductType(invoice.getProductType());
        row.setProductId(invoice.productId());
        row.setAttachmentPath(invoice.getPdfPath());
        row.setAttachmentName(invoice.fileName());
        row.setSentBy(actor);
        applyWhatsAppOutcome(row, delivery);
        logRepository.save(row);
        auditService.record(actor, delivery.sent() ? "WHATSAPP_SENT" : "WHATSAPP_FAILED", "Invoice", invoice.getId(),
                invoice.getInvoiceNumber() + (delivery.sent() ? " sent via " + delivery.provider() : " — " + delivery.reason()), ip);
        return delivery;
    }

    // ---- helpers ---------------------------------------------------------------------------

    private Map<String, Object> model(Payment payment, User student, String studentCode, Invoice invoice) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", student.getFullName());
        m.put("email", student.getEmail());
        m.put("studentId", studentCode == null ? "—" : studentCode);
        m.put("productName", payment.productName());
        m.put("productType", payment.getProductType().label());
        m.put("amount", InvoiceService.money(payment.getAmount(), payment.getCurrency()));
        m.put("paymentStatus", payment.getStatus() == PaymentStatus.SUCCESS ? "PAID" : payment.getStatus().name());
        Instant when = payment.getVerifiedAt() != null ? payment.getVerifiedAt() : Instant.now();
        m.put("paymentDate", DATE_TIME.format(when.atZone(INDIA)) + " IST");
        m.put("transactionId", invoice != null && invoice.getTransactionId() != null ? invoice.getTransactionId()
                : payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : "—");
        m.put("invoiceNumber", invoice == null ? "—" : invoice.getInvoiceNumber());
        m.put("orderRef", payment.getOrderRef());
        m.put("portalUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/student-login.html");
        return m;
    }

    private EmailAttachment attachmentFor(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        try {
            return EmailAttachment.pdf(invoice.fileName(), invoiceService.pdfBytes(invoice));
        } catch (RuntimeException e) {
            log.error("[INVOICE] Could not attach {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            return null;
        }
    }

    private void record(CommunicationType kind, User student, String studentCode, String to, String subject, Payment payment,
                        Invoice invoice, EmailDelivery delivery, User actor) {
        CommunicationLog row = new CommunicationLog();
        row.setChannel(CommunicationChannel.EMAIL);
        row.setMessageType(kind);
        row.setStudent(student);
        row.setStudentCode(studentCode);
        row.setRecipient(to);
        row.setSubject(subject);
        row.setBody(payment.getProductType().label() + ": " + payment.productName() + " — " + payment.getOrderRef()
                + (invoice == null ? "" : " — invoice " + invoice.getInvoiceNumber()));
        row.setInvoice(invoice);
        row.setProductType(payment.getProductType());
        row.setProductId(payment.productId());
        if (invoice != null) {
            row.setAttachmentPath(invoice.getPdfPath());
            row.setAttachmentName(invoice.fileName());
        }
        row.setSentBy(actor);
        applyEmailOutcome(row, delivery);
        logRepository.save(row);
        if (delivery.delivered() && invoice != null) {
            invoiceService.markEmailed(invoice);
        }
        auditService.record(actor, delivery.delivered() ? "EMAIL_SENT" : "EMAIL_FAILED", "User", student.getId(),
                kind + " to " + to + (delivery.delivered() ? " via " + delivery.provider() : " — " + delivery.status() + ": " + delivery.reason()), null);
    }

    static void applyEmailOutcome(CommunicationLog row, EmailDelivery delivery) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        row.setProvider(delivery.provider() != null ? delivery.provider() : (delivery.delivered() ? "SMTP" : "NONE"));
        if (delivery.delivered()) {
            row.setStatus(CommunicationStatus.SENT);
            row.setSentAt(Instant.now());
            row.setErrorReason(null);
        } else {
            // DISABLED (no mail channel) is a failure from the student's point of view and is retryable.
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorReason(delivery.reason());
        }
    }

    static void applyWhatsAppOutcome(CommunicationLog row, WhatsAppDelivery delivery) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        row.setProvider(delivery.provider());
        if (delivery.sent()) {
            row.setStatus(CommunicationStatus.SENT);
            row.setSentAt(Instant.now());
            row.setProviderMessageId(delivery.providerMessageId());
            row.setErrorReason(null);
        } else {
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorReason(delivery.reason());
        }
    }
}
