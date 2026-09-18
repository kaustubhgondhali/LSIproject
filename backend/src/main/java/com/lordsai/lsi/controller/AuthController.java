package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.ChangePasswordRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.ChangeUserIdRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.ForgotPasswordRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.LoginRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.LoginResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.ResendSetupRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.ResetPasswordRequest;
import com.lordsai.lsi.dto.auth.AuthDtos.TokenCheckResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.UserSummary;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.AuthService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        LoginResponse response = authService.login(body.identifier(), body.password(), body.portal(),
                RequestUtil.deviceInfo(request), RequestUtil.clientIp(request));
        return ApiResponse.ok("Login successful.", response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        CurrentUser.get().ifPresent(user -> authService.logout(user, RequestUtil.clientIp(request)));
        return ApiResponse.message("You have been logged out.");
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh() {
        return ApiResponse.ok(authService.refresh(CurrentUser.require()));
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.ok(authService.me(CurrentUser.require()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body,
                                            HttpServletRequest request) {
        authService.forgotPassword(body.email(), RequestUtil.clientIp(request));
        return ApiResponse.message("If an account exists for that email, a reset link has been sent.");
    }

    /** Student-facing recovery for a lost, expired or never-delivered enrollment email. */
    @PostMapping("/resend-setup")
    public ApiResponse<Void> resendSetup(@Valid @RequestBody ResendSetupRequest body,
                                         HttpServletRequest request) {
        authService.resendSetupEmail(body.email(), RequestUtil.clientIp(request));
        return ApiResponse.message(
                "If that email has an enrollment, we have re-sent the Student ID and password setup link.");
    }

    @GetMapping("/token-check")
    public ApiResponse<TokenCheckResponse> tokenCheck(@RequestParam("token") String token) {
        return ApiResponse.ok(authService.checkToken(token));
    }

    /** Used by both the first-time setup page and the reset page. */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body,
                                           HttpServletRequest request) {
        authService.setPasswordWithToken(body.token(), body.newPassword(), RequestUtil.clientIp(request));
        return ApiResponse.message("Your password has been set. You can now log in.");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                            HttpServletRequest request) {
        authService.changePassword(CurrentUser.require(), body.currentPassword(), body.newPassword(),
                RequestUtil.clientIp(request));
        return ApiResponse.message("Password changed. Please log in again on all devices.");
    }

    @PostMapping("/change-user-id")
    public ApiResponse<Void> changeUserId(@Valid @RequestBody ChangeUserIdRequest body,
                                          HttpServletRequest request) {
        authService.changeAdminUserId(CurrentUser.require(), body.currentUserId(), body.newUserId(),
                body.currentPassword(), RequestUtil.clientIp(request));
        return ApiResponse.message("User ID changed successfully.");
    }
}
