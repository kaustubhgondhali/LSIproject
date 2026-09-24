package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.ebook.EbookDtos.EntitlementResponse;
import com.lordsai.lsi.dto.ebook.EbookDtos.MyEbook;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.EbookEntitlement;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.EbookEntitlementRepository;
import com.lordsai.lsi.repository.InvoiceRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ebook access: the counterpart of {@link EnrollmentService} for the EBOOK product type. Reuses
 * the same student identity ({@link UserService#findOrCreateStudent}), the same setup-link
 * mechanism, the same invoice service and the same purchase email pipeline — one student
 * account, many product entitlements.
 */
@Service
public class EbookEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EbookEntitlementService.class);

    private final EbookEntitlementRepository entitlementRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final EbookService ebookService;
    private final AccountTokenService accountTokenService;
    private final InvoiceService invoiceService;
    private final PurchaseCommunicationService purchaseCommunication;
    private final AuditService auditService;

    public EbookEntitlementService(EbookEntitlementRepository entitlementRepository,
                                   StudentProfileRepository studentProfileRepository,
                                   InvoiceRepository invoiceRepository,
                                   UserService userService,
                                   EbookService ebookService,
                                   AccountTokenService accountTokenService,
                                   InvoiceService invoiceService,
                                   PaymentRepository paymentRepository,
                                   PurchaseCommunicationService purchaseCommunication,
                                   AuditService auditService) {
        this.entitlementRepository = entitlementRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.invoiceRepository = invoiceRepository;
        this.userService = userService;
        this.ebookService = ebookService;
        this.accountTokenService = accountTokenService;
        this.invoiceService = invoiceService;
        this.paymentRepository = paymentRepository;
        this.purchaseCommunication = purchaseCommunication;
        this.auditService = auditService;
    }

    /**
     * Turns a verified EBOOK payment into access. Idempotent: repeated calls for the same payment
     * (browser verify + webhook) find the same student, entitlement and invoice.
     */
    @Transactional
    public PurchaseFulfilment fulfilPayment(Payment payment) {
        Ebook ebook = payment.getEbook();
        log.info("[EBOOK] Payment verified: order={} status={} mode={} ebook={} email={}",
                payment.getOrderRef(), payment.getStatus(), payment.getPaymentMode(), ebook.getEbookCode(), payment.getCustomerEmail());

        UserService.StudentCreation creation = userService.findOrCreateStudent(
                payment.getCustomerName(), payment.getCustomerEmail(), payment.getCustomerMobile());
        User student = creation.user();
        log.info("[STUDENT] {} (userId={}, studentId={} {})", creation.created() ? "New student created" : "Existing student found",
                student.getId(), creation.profile().getStudentId(), creation.created() ? "generated" : "reused");

        if (payment.getUser() == null) {
            payment.setUser(student);
        }

        Optional<EbookEntitlement> existing = entitlementRepository.findByStudentIdAndEbookId(student.getId(), ebook.getId());
        boolean created = existing.isEmpty();
        EbookEntitlement entitlement = existing.orElseGet(() -> {
            EbookEntitlement e = new EbookEntitlement();
            e.setStudent(student);
            e.setEbook(ebook);
            e.setPayment(payment);
            e.setSource(EnrollmentSource.PAYMENT);
            e.setStatus(EntitlementStatus.ACTIVE);
            e.setGrantedAt(Instant.now());
            return entitlementRepository.save(e);
        });
        if (!created && entitlement.getStatus() != EntitlementStatus.ACTIVE) {
            // A revoked entitlement is re-activated by a fresh payment.
            entitlement.setStatus(EntitlementStatus.ACTIVE);
            entitlement.setGrantedAt(Instant.now());
            if (entitlement.getPayment() == null) {
                entitlement.setPayment(payment);
            }
            entitlementRepository.save(entitlement);
        }
        log.info("[EBOOK] Entitlement {} (id={}, status={})", created ? "created" : "reused", entitlement.getId(), entitlement.getStatus());

        String setupLink = null;
        if (student.getAccountStatus() == AccountStatus.PENDING_SETUP) {
            setupLink = accountTokenService.issueLink(student, TokenPurpose.ACCOUNT_SETUP);
            log.info("[ACCOUNT SETUP] Setup token generated for userId={} (valid 48h)", student.getId());
        }

        Invoice invoice = invoiceService.createForPayment(payment, student, creation.profile());
        EmailDelivery delivery = purchaseCommunication.sendPurchaseConfirmation(payment, student,
                creation.profile().getStudentId(), invoice, creation.created(), setupLink, creation.temporaryPassword());
        if (!delivery.delivered()) {
            log.error("[EMAIL] Ebook purchase email NOT delivered to {} — {}: {}", student.getEmail(), delivery.status(), delivery.reason());
        }

        auditService.record(null, creation.created() ? "STUDENT_CREATED_BY_PURCHASE" : "STUDENT_MATCHED_BY_PURCHASE",
                "User", student.getId(), "Payment " + payment.getOrderRef() + " for ebook " + ebook.getEbookCode(), null);
        auditService.record(null, created ? "EBOOK_ENTITLEMENT_CREATED" : "EBOOK_ENTITLEMENT_REUSED",
                "EbookEntitlement", entitlement.getId(),
                student.getEmail() + " -> " + ebook.getEbookCode() + " via payment " + payment.getOrderRef(), null);
        if (!delivery.delivered()) {
            auditService.record(null, "PURCHASE_EMAIL_NOT_DELIVERED", "User", student.getId(),
                    student.getEmail() + ": " + delivery.status() + " — " + delivery.reason(), null);
        }
        return new PurchaseFulfilment(student, creation.profile(), entitlement.getId(), creation.created(), created, delivery, invoice);
    }

        /** Replaces a lost ebook credential and resends the latest ebook purchase email with its invoice. */
        @Transactional
        public EmailDelivery resendLatestPurchaseEmail(User student) {
        Payment payment = paymentRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).stream()
            .filter(p -> p.isEbook() && p.getStatus() == com.lordsai.lsi.entity.enums.PaymentStatus.SUCCESS)
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No completed ebook purchase was found for this email."));
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId())
            .orElseThrow(() -> ResourceNotFoundException.of("Student", student.getId()));
        Invoice invoice = invoiceService.createForPayment(payment, student, profile);
        String replacementPassword = userService.issueReplacementPassword(student);
        return purchaseCommunication.sendPurchaseConfirmation(payment, student, profile.getStudentId(), invoice,
            true, null, replacementPassword);
        }

    /** Refuses a second checkout for an ebook the student already owns. */
    @Transactional(readOnly = true)
    public void assertNotAlreadyEntitled(String email, Long ebookId) {
        userService.findByEmail(email).ifPresent(user -> assertNotAlreadyEntitled(user.getId(), ebookId));
    }

    @Transactional(readOnly = true)
    public void assertNotAlreadyEntitled(Long studentUserId, Long ebookId) {
        entitlementRepository.findByStudentIdAndEbookId(studentUserId, ebookId)
                .filter(EbookEntitlement::grantsAccess)
                .ifPresent(e -> {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "You already have access to this ebook. Please log in to the Student Portal to read it.");
                });
    }

    /** The single gate in front of every ebook byte served: active entitlement owned by this student. */
    @Transactional(readOnly = true)
    public EbookEntitlement requireAccess(Long studentUserId, Long ebookId) {
        return entitlementRepository.findByStudentIdAndEbookId(studentUserId, ebookId)
                .filter(EbookEntitlement::grantsAccess)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this ebook."));
    }

    /** Resolves the protected ebook while the entitlement transaction is open. */
    @Transactional(readOnly = true)
    public Ebook requireAccessibleEbook(Long studentUserId, Long ebookId) {
        return entitlementRepository.findWithEbookByStudentIdAndEbookId(studentUserId, ebookId)
                .filter(EbookEntitlement::grantsAccess)
                .map(EbookEntitlement::getEbook)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this ebook."));
    }

    @Transactional(readOnly = true)
    public List<MyEbook> myEbooks(Long studentUserId) {
        return entitlementRepository.findByStudentIdOrderByGrantedAtDesc(studentUserId).stream().map(this::toMyEbook).toList();
    }

    // ---- Admin ----------------------------------------------------------------------------

    @Transactional
    public EntitlementResponse adminGrant(Long studentUserId, Long ebookId, User actor, String ip) {
        User student = userService.requireUser(studentUserId, Role.STUDENT);
        Ebook ebook = ebookService.requireEbook(ebookId);
        if (entitlementRepository.existsByStudentIdAndEbookId(studentUserId, ebookId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This student already has access to this ebook.");
        }
        EbookEntitlement e = new EbookEntitlement();
        e.setStudent(student);
        e.setEbook(ebook);
        e.setSource(EnrollmentSource.ADMIN_MANUAL);
        e.setStatus(EntitlementStatus.ACTIVE);
        e.setGrantedAt(Instant.now());
        e.setCreatedBy(actor);
        e = entitlementRepository.save(e);
        auditService.record(actor, "EBOOK_ENTITLEMENT_GRANTED", "EbookEntitlement", e.getId(),
                "Manually granted '" + ebook.getTitle() + "' to " + student.getEmail(), ip);
        return toResponse(e);
    }

    @Transactional
    public EntitlementResponse setStatus(Long id, EntitlementStatus status, User actor, String ip) {
        EbookEntitlement e = entitlementRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Ebook entitlement", id));
        EntitlementStatus old = e.getStatus();
        e.setStatus(status);
        entitlementRepository.save(e);
        auditService.record(actor, "EBOOK_ENTITLEMENT_STATUS_CHANGED", "EbookEntitlement", id, old + " -> " + status, ip);
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public List<EntitlementResponse> listForStudent(Long studentUserId) {
        return entitlementRepository.findByStudentIdOrderByGrantedAtDesc(studentUserId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EntitlementResponse> listForEbook(Long ebookId) {
        return entitlementRepository.findByEbookId(ebookId).stream().map(this::toResponse).toList();
    }

    // ---- Mapping --------------------------------------------------------------------------

    private MyEbook toMyEbook(EbookEntitlement e) {
        Ebook b = e.getEbook();
        Invoice invoice = e.getPayment() == null ? null : invoiceRepository.findByPaymentId(e.getPayment().getId()).orElse(null);
        return new MyEbook(e.getId(), b.getId(), b.getEbookCode(), b.getTitle(), b.getAuthor(), b.getShortDescription(),
                b.getCategory(), b.getLanguage(), b.getCoverImagePath(), e.getStatus(), e.getGrantedAt(),
                invoice == null ? null : invoice.getId(), invoice == null ? null : invoice.getInvoiceNumber(),
                b.getPdfPath() != null);
    }

    public EntitlementResponse toResponse(EbookEntitlement e) {
        User s = e.getStudent();
        Ebook b = e.getEbook();
        String studentId = studentProfileRepository.findByUserId(s.getId()).map(StudentProfile::getStudentId).orElse(null);
        return new EntitlementResponse(e.getId(), s.getId(), studentId, s.getFullName(), s.getEmail(), b.getId(), b.getEbookCode(),
                b.getTitle(), e.getStatus(), e.getSource().name(),
                e.getPayment() == null ? null : e.getPayment().getId(),
                e.getPayment() == null ? null : e.getPayment().getOrderRef(), e.getGrantedAt());
    }
}
