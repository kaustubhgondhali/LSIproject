package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.payment.GatewayDtos.DemoPaymentRequest;
import com.lordsai.lsi.dto.payment.GatewayDtos.PublicPaymentMode;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.CreateOrderResponse;
import com.lordsai.lsi.dto.payment.PaymentDtos.VerifyRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.VerifyResponse;
import com.lordsai.lsi.service.PaymentGatewayConfigService;
import com.lordsai.lsi.service.PaymentService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public checkout endpoints used by courses.html. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentGatewayConfigService gatewayConfigService;

    public PaymentController(PaymentService paymentService, PaymentGatewayConfigService gatewayConfigService) {
        this.paymentService = paymentService;
        this.gatewayConfigService = gatewayConfigService;
    }

    /** Tells the checkout whether to open Razorpay or show the demo payment. Public, no secrets. */
    @GetMapping("/mode")
    public ApiResponse<PublicPaymentMode> mode() {
        return ApiResponse.ok(gatewayConfigService.publicMode());
    }

    /** Demo enrollment; the service refuses this whenever Razorpay is configured. */
    @PostMapping("/demo-complete")
    public ApiResponse<VerifyResponse> demoComplete(@Valid @RequestBody DemoPaymentRequest body, HttpServletRequest request) {
        VerifyResponse response = paymentService.completeDemoPayment(body, RequestUtil.clientIp(request));
        return ApiResponse.ok(response.message(), response);
    }

    @PostMapping("/create-order")
    public ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(paymentService.createOrder(body, RequestUtil.clientIp(request)));
    }

    @PostMapping("/verify")
    public ApiResponse<VerifyResponse> verify(@Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        VerifyResponse response = paymentService.verify(body, RequestUtil.clientIp(request));
        return ApiResponse.ok(response.message(), response);
    }

    /** Razorpay posts here server-to-server. The raw body is needed for signature verification. */
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(@RequestBody String rawBody,
                                     @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ApiResponse.message("ok");
    }
}
