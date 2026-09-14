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
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentRepository paymentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final PaymentGateway gateway;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          StudentProfileRepository studentProfileRepository,
                          CourseService courseService,
                          EnrollmentService enrollmentService,
                          PaymentGateway gateway,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.gateway = gateway;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---- Step 1: create order -------------------------------------------------------------

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest req, String ip) {
        if (!gateway.isConfigured()) {
            // Demo mode: the checkout must use /demo-complete instead of a real order.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payments are not configured. The website is in demo payment mode.");
        }
        Course course = courseService.requirePurchasableCourse(req.courseId());
        String email = AuthService.normalizeEmail(req.email());
        enrollmentService.assertNotAlreadyEnrolled(email, course.getId());

        // The amount is read from the course record. Nothing in the request can influence it.
        var amount = course.effectivePrice();
        String orderRef = nextOrderRef("LSI-ORD");

        PaymentGateway.GatewayOrder order = gateway.createOrder(amount, orderRef, Map.of(
                "course_code", course.getCourseCode(),
                "customer_email", email,
                "order_ref", orderRef));

        Payment payment = new Payment();
        payment.setOrderRef(orderRef);
        payment.setRazorpayOrderId(order.gatewayOrderId());
        payment.setCustomerName(req.fullName().trim());
        payment.setCustomerEmail(email);
        payment.setCustomerMobile(UserService.cleanMobile(req.mobile()));
        payment.setCourse(course);
        payment.setAmount(amount);
        payment.setCurrency(order.currency());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setPaymentMode(PaymentMode.RAZORPAY);
        payment = paymentRepository.save(payment);

        auditService.record(null, "PAYMENT_ORDER_CREATED", "Payment", payment.getId(),
                orderRef + " for " + course.getCourseCode() + " by " + email + " (" + amount + ")", ip);

        return new CreateOrderResponse(orderRef, order.gatewayOrderId(), gateway.publicKeyId(),
                order.amountMinor(), amount, order.currency(), course.getCourseName(),
                payment.getCustomerName(), payment.getCustomerEmail(), payment.getCustomerMobile());
    }

    // ---- Demo mode (only while no gateway is configured) -------------------------------------

    /**
     * Enrolls a student without charging anyone. Refused outright whenever Razorpay is active,
     * so "gateway configured + demo enrollment" is impossible regardless of what the browser sends.
     */
    @Transactional
    public VerifyResponse completeDemoPayment(DemoPaymentRequest req, String ip) {
        if (gateway.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Real payments are active. Please complete the payment through Razorpay.");
        }
        Course course = courseService.requirePurchasableCourse(req.courseId());
        String email = AuthService.normalizeEmail(req.email());
        enrollmentService.assertNotAlreadyEnrolled(email, course.getId());

        String orderRef = nextOrderRef("LSI-DEMO");
        Payment payment = new Payment();
        payment.setOrderRef(orderRef);
        // Column is NOT NULL / unique; a synthetic id keeps demo rows distinguishable and never collides.
        payment.setRazorpayOrderId("demo_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setRazorpayPaymentId(null);
        payment.setCustomerName(req.fullName().trim());
        payment.setCustomerEmail(email);
        payment.setCustomerMobile(UserService.cleanMobile(req.mobile()));
        payment.setCourse(course);
        payment.setAmount(course.effectivePrice());
        payment.setCurrency("INR");
        payment.setPaymentMode(PaymentMode.DEMO);
        payment.setPaymentMethod("demo");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setVerifiedAt(Instant.now());
        payment = paymentRepository.save(payment);

        EnrollmentService.Fulfilment fulfilment = enrollmentService.fulfilPayment(payment);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        auditService.record(null, "DEMO_PAYMENT_COMPLETED", "Payment", payment.getId(),
                orderRef + " for " + course.getCourseCode() + " by " + email + " (no money charged); enrollment "
                        + fulfilment.enrollment().getId(), ip);
        return successResponse(payment, fulfilment.newAccount(), fulfilment.newEnrollment());
    }

    // ---- Step 2: verify browser callback ----------------------------------------------------

    @Transactional(noRollbackFor = ApiException.class)
    public VerifyResponse verify(VerifyRequest req, String ip) {
        Payment payment = paymentRepository.findByRazorpayOrderId(req.razorpayOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment order not found."));

        // Duplicate callback for an already-processed payment: return the same success answer.
        if (payment.getStatus() == PaymentStatus.SUCCESS && payment.getProcessedAt() != null) {
            return successResponse(payment, false, false);
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

        EnrollmentService.Fulfilment fulfilment = enrollmentService.fulfilPayment(payment);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        auditService.record(null, "PAYMENT_VERIFIED", "Payment", payment.getId(),
                payment.getOrderRef() + " verified; enrollment " + fulfilment.enrollment().getId(), ip);
        return successResponse(payment, fulfilment.newAccount(), fulfilment.newEnrollment());
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
                EnrollmentService.Fulfilment fulfilment = enrollmentService.fulfilPayment(payment);
                payment.setProcessedAt(Instant.now());
                paymentRepository.save(payment);
                auditService.record(null, "PAYMENT_VERIFIED_WEBHOOK", "Payment", payment.getId(),
                        payment.getOrderRef() + " fulfilled by webhook; enrollment " + fulfilment.enrollment().getId(), null);
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
        return new AdminPayment(p.getId(), p.getOrderRef(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getUser() == null ? null : p.getUser().getId(), studentId,
                p.getCustomerName(), p.getCustomerEmail(), p.getCustomerMobile(),
                p.getCourse().getId(), p.getCourse().getCourseName(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getPaymentMode(), p.getPaymentMethod(), p.getFailureReason(), p.getCreatedAt(), p.getVerifiedAt());
    }

    // ---- helpers --------------------------------------------------------------------------

    private VerifyResponse successResponse(Payment payment, boolean newAccount, boolean newEnrollment) {
        String studentId = payment.getUser() == null ? null
                : studentProfileRepository.findByUserId(payment.getUser().getId()).map(StudentProfile::getStudentId).orElse(null);
        String prefix = payment.getPaymentMode() == PaymentMode.DEMO ? "Demo payment recorded — no money was charged." : "Payment successful.";
        String message = newAccount
                ? prefix + " We have emailed your Student ID and a link to set your password."
                : newEnrollment
                    ? prefix + " The course has been added to your existing Student Portal account."
                    : prefix + " Your enrollment is active.";
        return new VerifyResponse(payment.getStatus(), payment.getPaymentMode(), payment.getOrderRef(), payment.getCourse().getCourseName(),
                payment.getAmount(), com.lordsai.lsi.entity.enums.EnrollmentStatus.ACTIVE,
                studentId, payment.getCustomerEmail(), newAccount, message);
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
