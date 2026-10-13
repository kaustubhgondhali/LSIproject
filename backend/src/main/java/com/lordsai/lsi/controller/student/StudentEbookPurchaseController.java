package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.payment.GatewayDtos.DemoPaymentRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.AdminPayment;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderResponse;
import com.lordsai.lsi.dto.payment.PaymentDtos.VerifyResponse;
import com.lordsai.lsi.dto.user.UserDtos;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.PaymentService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.Objects;

/**
 * Buying an ebook from inside the Student Portal (My Ebooks -> Buy ebooks). The student fills in
 * nothing: the buyer's name, email and mobile are the ones already on the account (from the course
 * purchase), read on the server — never from the request — so the ebook can only land in this
 * account. The order then runs through the same {@link PaymentService} pipeline as the public store:
 * price from the database, Razorpay signature verified server-side by POST /api/payments/verify, demo
 * mode refused whenever Razorpay is configured, and a second purchase of an owned ebook refused.
 */
@RestController
@RequestMapping("/api/student/ebooks")
public class StudentEbookPurchaseController {

    private final PaymentService paymentService;
    private final UserService userService;

    public StudentEbookPurchaseController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    /** Razorpay mode: creates the order; the portal then opens Razorpay Checkout and calls /payments/verify. */
    @PostMapping("/{ebookId}/order")
    public ApiResponse<CreateOrderResponse> order(@PathVariable Long ebookId, HttpServletRequest request) {
        User student = me();
        CreateOrderRequest order = new CreateOrderRequest(null, ProductType.EBOOK, ebookId,
                student.getFullName(), student.getEmail(), knownMobile(student));
        return ApiResponse.ok(paymentService.createOrder(order, RequestUtil.clientIp(request)));
    }

    /** Demo mode only (no gateway configured); the service refuses it while Razorpay is active. */
    @PostMapping("/{ebookId}/demo-complete")
    public ApiResponse<VerifyResponse> demoComplete(@PathVariable Long ebookId, HttpServletRequest request) {
        User student = me();
        DemoPaymentRequest demo = new DemoPaymentRequest(null, ProductType.EBOOK, ebookId,
                student.getFullName(), student.getEmail(), knownMobile(student));
        VerifyResponse response = paymentService.completeDemoPayment(demo, RequestUtil.clientIp(request));
        return ApiResponse.ok(response.message(), response);
    }

    private User me() {
        return userService.requireUser(CurrentUser.require().id(), Role.STUDENT);
    }

    /**
     * The payment record needs a contact mobile: the one on the account, else the one the student
     * gave at their latest successful purchase (e.g. the course), else any earlier purchase.
     */
    private String knownMobile(User student) {
        String mobile = validMobile(student.getMobile());
        if (mobile != null) {
            return mobile;
        }
        return paymentService.listForUser(student.getId()).stream()
                .sorted(Comparator.comparing((AdminPayment p) -> p.status() != PaymentStatus.SUCCESS))
                .map(p -> validMobile(p.customerMobile()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "We don't have a mobile number on your account yet. Please contact the academy to add it, then buy the ebook."));
    }

    private static String validMobile(String raw) {
        String mobile = UserService.cleanMobile(raw);
        return mobile != null && mobile.matches(UserDtos.MOBILE_PATTERN) ? mobile : null;
    }
}
