package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.payment.GatewayDtos.GatewayStatus;
import com.lordsai.lsi.dto.payment.GatewayDtos.SaveGatewayRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.PaymentGatewayConfigService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Main-admin only (enforced by the /api/admin/** rule in SecurityConfig). Never returns a secret. */
@RestController
@RequestMapping("/api/admin/payment-gateway")
public class AdminPaymentGatewayController {

    private final PaymentGatewayConfigService service;
    private final UserService userService;

    public AdminPaymentGatewayController(PaymentGatewayConfigService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<GatewayStatus> status() {
        return ApiResponse.ok(service.status());
    }

    /** Create or update. Validates against Razorpay before enabling real payments. */
    @PutMapping
    public ApiResponse<GatewayStatus> save(@Valid @RequestBody SaveGatewayRequest body, HttpServletRequest req) {
        GatewayStatus status = service.save(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Razorpay credentials verified. Real payments are now active.", status);
    }

    @PatchMapping("/disable")
    public ApiResponse<GatewayStatus> disable(HttpServletRequest req) {
        return ApiResponse.ok("Razorpay disabled. Demo payments are now active.", service.disable(actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/enable")
    public ApiResponse<GatewayStatus> enable(HttpServletRequest req) {
        return ApiResponse.ok("Razorpay re-enabled. Real payments are now active.", service.enable(actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping
    public ApiResponse<GatewayStatus> remove(HttpServletRequest req) {
        return ApiResponse.ok("Razorpay configuration removed. Demo payments are now active.", service.remove(actor(), RequestUtil.clientIp(req)));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
