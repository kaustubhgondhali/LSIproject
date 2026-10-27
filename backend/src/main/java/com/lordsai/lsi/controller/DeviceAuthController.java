package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.LoginResponse;
import com.lordsai.lsi.dto.auth.DeviceBindingDtos.DeviceLinkRequest;
import com.lordsai.lsi.dto.auth.DeviceBindingDtos.DeviceRegisterRequest;
import com.lordsai.lsi.dto.auth.DeviceBindingDtos.DeviceResetConfirmRequest;
import com.lordsai.lsi.dto.auth.DeviceBindingDtos.DeviceResetInitRequest;
import com.lordsai.lsi.service.DeviceBindingService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoints for device registration, linking secondary browsers on the same computer,
 * and self-service device reset recovery.
 */
@RestController
@RequestMapping("/api/auth/device")
public class DeviceAuthController {

    private final DeviceBindingService deviceBindingService;

    public DeviceAuthController(DeviceBindingService deviceBindingService) {
        this.deviceBindingService = deviceBindingService;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> registerDevice(@Valid @RequestBody DeviceRegisterRequest body,
                                                     HttpServletRequest request) {
        LoginResponse response = deviceBindingService.completeRegistration(
                body.tempToken(),
                body.otp(),
                body.publicKey(),
                body.deviceName(),
                body.devicePlatform() != null ? body.devicePlatform() : RequestUtil.deviceInfo(request),
                RequestUtil.clientIp(request)
        );
        return ApiResponse.ok("Device registered successfully.", response);
    }

    @PostMapping("/link-browser")
    public ApiResponse<LoginResponse> linkBrowser(@Valid @RequestBody DeviceLinkRequest body,
                                                  HttpServletRequest request) {
        LoginResponse response = deviceBindingService.completeBrowserLink(
                body.tempToken(),
                body.otp(),
                body.publicKey(),
                body.devicePlatform() != null ? body.devicePlatform() : RequestUtil.deviceInfo(request),
                RequestUtil.clientIp(request)
        );
        return ApiResponse.ok("Browser authorized on your registered device.", response);
    }

    @PostMapping("/reset-request")
    public ApiResponse<Map<String, String>> requestReset(@Valid @RequestBody DeviceResetInitRequest body,
                                                         HttpServletRequest request) {
        String tempToken = deviceBindingService.initiateDeviceReset(body.identifier(), RequestUtil.clientIp(request));
        return ApiResponse.ok("A device reset verification code has been sent to your registered email.",
                Map.of("tempToken", tempToken));
    }

    @PostMapping("/reset-confirm")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody DeviceResetConfirmRequest body,
                                          HttpServletRequest request) {
        deviceBindingService.confirmDeviceReset(body.tempToken(), body.otp(), RequestUtil.clientIp(request));
        return ApiResponse.message("Device reset confirmed. You may now log in and register your new computer.");
    }
}

