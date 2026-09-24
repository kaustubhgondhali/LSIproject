package com.lordsai.lsi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.dto.payment.PaymentDtos.AdminPayment;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderResponse;
import com.lordsai.lsi.dto.payment.GatewayDtos.DemoPaymentRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.VerifyRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.VerifyResponse;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.InvoiceRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * The single Razorpay purchase pipeline for every digital product (courses and ebooks):
 * create order (price from the database) -> verify signature server-side -> fulfil (student,
 * entitlement, invoice, email) -> webhook as the idempotent safety net.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentRepository paymentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InvoiceRepository invoiceRepository;
    private final CourseService courseService;
    private final EbookService ebookService;
    private final EnrollmentService enrollmentService;
    private final EbookEntitlementService ebookEntitlementService;
    private final PaymentGateway gateway;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          StudentProfileRepository studentProfileRepository,
                          InvoiceRepository invoiceRepository,
                          CourseService courseService,
                          EbookService ebookService,
                          EnrollmentService enrollmentService,
                          EbookEntitlementService ebookEntitlementService,
                          PaymentGateway gateway,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.invoiceRepository = invoiceRepository;
        this.courseService = courseService;
        this.ebookService = ebookService;
        this.enrollmentService = enrollmentService;
        this.ebookEntitlementService = ebookEntitlementService;
        this.gateway = gateway;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** The product being bought, resolved from the database. The client never supplies a price. */
    private record Product(ProductType type, Course course, Ebook ebook) {
        BigDecimal price() {
            return type == ProductType.EBOOK ? ebook.effectivePrice() : course.effectivePrice();
        }

        String code() {
            return type == ProductType.EBOOK ? ebook.getEbookCode() : course.getCourseCode();
        }

        String name() {
            return type == ProductType.EBOOK ? ebook.getTitle() : course.getCourseName();
        }
    }

    private Product resolveProduct(ProductType requestedType, Long courseId, Long ebookId, String email) {
        ProductType type = requestedType != null ? requestedType : (ebookId != null && courseId == null ? ProductType.EBOOK : ProductType.COURSE);
        if (type == ProductType.EBOOK) {
            if (ebookId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an ebook.");
            }
            Ebook ebook = ebookService.requirePurchasableEbook(ebookId);
            ebookEntitlementService.assertNotAlreadyEntitled(email, ebook.getId());
            return new Product(type, null, ebook);
        }
        if (courseId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a course.");
        }
        Course course = courseService.requirePurchasableCourse(courseId);
        enrollmentService.assertNotAlreadyEnrolled(email, course.getId());
        return new Product(type, course, null);
    }

    // ---- Step 1: create order -------------------------------------------------------------

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest req, String ip) {
        if (!gateway.isConfigured()) {
            // Demo mode: the checkout must use /demo-complete instead of a real order.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payments are not configured. The website is in demo payment mode.");
        }
        String email = AuthService.normalizeEmail(req.email());
        Product product = resolveProduct(req.productType(), req.courseId(), req.ebookId(), email);

        // The amount is read from the product record. Nothing in the request can influence it.
        BigDecimal amount = product.price();
        String orderRef = nextOrderRef("LSI-ORD");

        PaymentGateway.GatewayOrder order = gateway.createOrder(amount, orderRef, Map.of(
                "product_type", product.type().name(),
                "product_code", product.code(),
                "customer_email", email,
                "order_ref", orderRef));

        Payment payment = new Payment();
        payment.setOrderRef(orderRef);
        payment.setRazorpayOrderId(order.gatewayOrderId());
        payment.setCustomerName(req.fullName().trim());
        payment.setCustomerEmail(email);
        payment.setCustomerMobile(UserService.cleanMobile(req.mobile()));
        payment.setProductType(product.type());
        payment.setCourse(product.course());
        payment.setEbook(product.ebook());
        payment.setAmount(amount);
        payment.setCurrency(order.currency());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setPaymentMode(PaymentMode.RAZORPAY);
        payment = paymentRepository.save(payment);

        auditService.record(null, "PAYMENT_ORDER_CREATED", "Payment", payment.getId(),
                orderRef + " for " + product.type() + " " + product.code() + " by " + email + " (" + amount + ")", ip);

        return new CreateOrderResponse(orderRef, order.gatewayOrderId(), gateway.publicKeyId(),
                order.amountMinor(), amount, order.currency(), product.name(), product.type(), product.name(),
                payment.getCustomerName(), payment.getCustomerEmail(), payment.getCustomerMobile());
    }

    // ---- Demo mode (only while no gateway is configured) -------------------------------------

    /**
     * Grants access without charging anyone. Refused outright whenever Razorpay is active,
     * so "gateway configured + demo purchase" is impossible regardless of what the browser sends.
     */
    @Transactional
    public VerifyResponse completeDemoPayment(DemoPaymentRequest req, String ip) {
        if (gateway.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Real payments are active. Please complete the payment through Razorpay.");
        }
        String email = AuthService.normalizeEmail(req.email());
        Product product = resolveProduct(req.productType(), req.courseId(), req.ebookId(), email);

        String orderRef = nextOrderRef("LSI-DEMO");
        Payment payment = new Payment();
        payment.setOrderRef(orderRef);
        // Column is NOT NULL / unique; a synthetic id keeps demo rows distinguishable and never collides.
        payment.setRazorpayOrderId("demo_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setRazorpayPaymentId(null);
        payment.setCustomerName(req.fullName().trim());
        payment.setCustomerEmail(email);
        payment.setCustomerMobile(UserService.cleanMobile(req.mobile()));
        payment.setProductType(product.type());
        payment.setCourse(product.course());
        payment.setEbook(product.ebook());
        payment.setAmount(product.price());
        payment.setCurrency("INR");
        payment.setPaymentMode(PaymentMode.DEMO);
        payment.setPaymentMethod("demo");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setVerifiedAt(Instant.now());
        payment = paymentRepository.save(payment);

        PurchaseFulfilment fulfilment = fulfil(payment);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        auditService.record(null, "DEMO_PAYMENT_COMPLETED", "Payment", payment.getId(),
                orderRef + " for " + product.type() + " " + product.code() + " by " + email + " (no money charged); entitlement "
                        + fulfilment.entitlementId(), ip);
        return successResponse(payment, fulfilment.newAccount(), fulfilment.newEntitlement(),
                fulfilment.purchaseEmail().delivered());
    }

    // ---- Step 2: verify browser callback ----------------------------------------------------

    @Transactional(noRollbackFor = ApiException.class)
    public VerifyResponse verify(VerifyRequest req, String ip) {
        Payment payment = paymentRepository.findByRazorpayOrderId(req.razorpayOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment order not found."));

        // Duplicate callback for an already-processed payment: return the same success answer.
        // Nothing is emailed on a repeat, and this branch's message makes no claim about email,
        // so it reports no failure either.
        if (payment.getStatus() == PaymentStatus.SUCCESS && payment.getProcessedAt() != null) {
            return successResponse(payment, false, false, true);
        }

        if (!gateway.verifyPaymentSignature(req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            payment.setRazorpayPaymentId(req.razorpayPaymentId());
            paymentRepository.save(payment);
            auditService.record(null, "PAYMENT_SIGNATURE_INVALID", "Payment", payment.getId(),
                    payment.getOrderRef() + " rejected: bad signature", ip);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Payment could not be verified. If money was deducted, please contact the academy with order " + payment.getOrderRef() + ".");
        }

        payment.setRazorpayPaymentId(req.razorpayPaymentId());
        payment.setRazorpaySignature(req.razorpaySignature());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setVerifiedAt(Instant.now());
        paymentRepository.save(payment);

        PurchaseFulfilment fulfilment = fulfil(payment);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        auditService.record(null, "PAYMENT_VERIFIED", "Payment", payment.getId(),
                payment.getOrderRef() + " verified; " + payment.getProductType() + " entitlement " + fulfilment.entitlementId()
                        + "; invoice " + (fulfilment.invoice() == null ? "—" : fulfilment.invoice().getInvoiceNumber()), ip);
        return successResponse(payment, fulfilment.newAccount(), fulfilment.newEntitlement(),
                fulfilment.purchaseEmail().delivered());
    }

    // ---- Step 3 (safety net): webhook -------------------------------------------------------

    /**
     * Reconciles a Razorpay webhook. If the browser callback never arrived (tab closed, network
     * drop), the captured-payment event still fulfils the order. Repeated deliveries are no-ops.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Webhook rejected: invalid signature");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook signature.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed webhook payload.");
        }

        String event = root.path("event").asText("");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        String paymentId = paymentEntity.path("id").asText(null);
        String method = paymentEntity.path("method").asText(null);

        if (orderId == null) {
            log.info("Webhook '{}' ignored: no order id", event);
            return;
        }
        Optional<Payment> maybe = paymentRepository.findByRazorpayOrderId(orderId);
        if (maybe.isEmpty()) {
            log.warn("Webhook '{}' for unknown order {}", event, orderId);
            return;
        }
        Payment payment = maybe.get();
        if (method != null) {
            payment.setPaymentMethod(method);
        }

        switch (event) {
            case "payment.captured", "order.paid" -> {
                if (payment.getStatus() == PaymentStatus.SUCCESS && payment.getProcessedAt() != null) {
                    paymentRepository.save(payment);
                    return;
                }
                long amountPaise = paymentEntity.path("amount").asLong(-1);
                long expectedPaise = payment.getAmount().movePointRight(2).longValueExact();
                if (amountPaise >= 0 && amountPaise != expectedPaise) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason("Amount mismatch: expected " + expectedPaise + " got " + amountPaise);
                    paymentRepository.save(payment);
                    auditService.record(null, "PAYMENT_AMOUNT_MISMATCH", "Payment", payment.getId(),
                            payment.getFailureReason(), null);
                    return;
                }
                payment.setRazorpayPaymentId(paymentId);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setVerifiedAt(Instant.now());
                paymentRepository.save(payment);
                PurchaseFulfilment fulfilment = fulfil(payment);
                payment.setProcessedAt(Instant.now());
                paymentRepository.save(payment);
                auditService.record(null, "PAYMENT_VERIFIED_WEBHOOK", "Payment", payment.getId(),
                        payment.getOrderRef() + " fulfilled by webhook; " + payment.getProductType() + " entitlement "
                                + fulfilment.entitlementId(), null);
            }
            case "payment.failed" -> {
                if (payment.getStatus() != PaymentStatus.SUCCESS) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setRazorpayPaymentId(paymentId);
                    payment.setFailureReason(paymentEntity.path("error_description").asText("Payment failed"));
                    paymentRepository.save(payment);
                    auditService.record(null, "PAYMENT_FAILED", "Payment", payment.getId(), payment.getFailureReason(), null);
                }
            }
            case "refund.processed" -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                auditService.record(null, "PAYMENT_REFUNDED", "Payment", payment.getId(), payment.getOrderRef(), null);
            }
            default -> {
                paymentRepository.save(payment);
                log.info("Webhook event '{}' recorded without state change", event);
            }
        }
    }

    /** Routes a verified payment to the entitlement service of its product type. */
    private PurchaseFulfilment fulfil(Payment payment) {
        return payment.isEbook() ? ebookEntitlementService.fulfilPayment(payment) : enrollmentService.fulfilPayment(payment);
    }

    // ---- Admin ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdminPayment> listAdmin(PaymentStatus status, Pageable pageable) {
        Page<Payment> page = status == null ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);
        return page.map(this::toAdmin);
    }

    @Transactional(readOnly = true)
    public AdminPayment getAdmin(Long id) {
        return toAdmin(paymentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Payment", id)));
    }

    @Transactional(readOnly = true)
    public java.util.List<AdminPayment> listForUser(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toAdmin).toList();
    }

    public AdminPayment toAdmin(Payment p) {
        String studentId = p.getUser() == null ? null
                : studentProfileRepository.findByUserId(p.getUser().getId()).map(StudentProfile::getStudentId).orElse(null);
        Invoice invoice = invoiceRepository.findByPaymentId(p.getId()).orElse(null);
        return new AdminPayment(p.getId(), p.getOrderRef(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getUser() == null ? null : p.getUser().getId(), studentId,
                p.getCustomerName(), p.getCustomerEmail(), p.getCustomerMobile(),
                p.getCourse() == null ? null : p.getCourse().getId(),
                p.productName(),
                p.getProductType(), p.productId(), p.productName(),
                invoice == null ? null : invoice.getInvoiceNumber(), invoice == null ? null : invoice.getId(),
                p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getPaymentMode(), p.getPaymentMethod(), p.getFailureReason(), p.getCreatedAt(), p.getVerifiedAt());
    }

    // ---- helpers --------------------------------------------------------------------------

    private VerifyResponse successResponse(Payment payment, boolean newAccount, boolean newEntitlement,
                                           boolean purchaseEmailSent) {
        String studentId = payment.getUser() == null ? null
                : studentProfileRepository.findByUserId(payment.getUser().getId()).map(StudentProfile::getStudentId).orElse(null);
        Invoice invoice = invoiceRepository.findByPaymentId(payment.getId()).orElse(null);
        String prefix = payment.getPaymentMode() == PaymentMode.DEMO ? "Demo payment recorded — no money was charged." : "Payment successful.";
        String product = payment.getProductType().label().toLowerCase();

        String message;
        if (!purchaseEmailSent) {
            // Never claim an email was sent when it was not: the setup link is the only way in.
            message = prefix + " Your " + product + " access is active, but we could not email your account details."
                    + " Please use \"Resend setup email\" below, or contact the academy with order " + payment.getOrderRef() + ".";
        } else if (newAccount) {
            message = prefix + (payment.isEbook()
                    ? " We have emailed your Student ID, initial password, purchase details and invoice."
                    : " We have emailed your Student ID, invoice and a link to set your password.");
        } else if (newEntitlement) {
            message = prefix + " The " + product + " has been added to your existing Student Portal account and the invoice has been emailed.";
        } else {
            message = prefix + " Your " + product + " access is active.";
        }

        return new VerifyResponse(payment.getStatus(), payment.getPaymentMode(), payment.getOrderRef(), payment.productName(),
                payment.getProductType(), payment.productName(),
                invoice == null ? null : invoice.getInvoiceNumber(), invoice == null ? null : invoice.getId(),
                payment.getAmount(), com.lordsai.lsi.entity.enums.EnrollmentStatus.ACTIVE,
                studentId, payment.getCustomerEmail(), newAccount, purchaseEmailSent, message);
    }

    private String nextOrderRef(String prefix) {
        String date = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(ORDER_DATE);
        String candidate;
        do {
            candidate = prefix + "-" + date + "-" + String.format("%06d", (int) (Math.random() * 1_000_000));
        } while (paymentRepository.findByOrderRef(candidate).isPresent());
        return candidate;
    }
}
