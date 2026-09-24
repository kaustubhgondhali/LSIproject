package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.ConnectionResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DetectRequest;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DetectionResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.EmailSettingsStatus;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.ProviderInfo;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.SaveEmailSettingsRequest;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.TestConnectionRequest;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.TestEmailRequest;
import com.lordsai.lsi.dto.admin.GoogleOAuthDtos.GoogleAuthorizationUrl;
import com.lordsai.lsi.dto.admin.GoogleOAuthDtos.GoogleStatus;
import com.lordsai.lsi.email.google.GmailOAuthService;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.EmailSettingsService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Main-admin only (enforced by the /api/admin/** rule in SecurityConfig). Never returns the SMTP password. */
@RestController
@RequestMapping("/api/admin/email-settings")
public class AdminEmailSettingsController {

    private final EmailSettingsService service;
    private final UserService userService;
    private final GmailOAuthService gmailOAuth;

    public AdminEmailSettingsController(EmailSettingsService service, UserService userService,
                                        GmailOAuthService gmailOAuth) {
        this.service = service;
        this.userService = userService;
        this.gmailOAuth = gmailOAuth;
    }

    @GetMapping
    public ApiResponse<EmailSettingsStatus> status() {
        return ApiResponse.ok(service.status());
    }

    /** The provider dropdown, from the central registry (no SMTP knowledge lives in the browser). */
    @GetMapping("/providers")
    public ApiResponse<List<ProviderInfo>> providers() {
        return ApiResponse.ok(service.providers());
    }

    /** "Auto Configure Email": sender address (+ optional provider choice) -> host / port / security / username. */
    @PostMapping("/detect")
    public ApiResponse<DetectionResult> detect(@Valid @RequestBody DetectRequest body) {
        DetectionResult r = service.detect(body);
        return ApiResponse.ok(r.message(), r);
    }

    /** "Test SMTP Connection": authenticates with the values in the form without saving them. */
    @PostMapping("/test-connection")
    public ApiResponse<ConnectionResult> testConnection(@Valid @RequestBody TestConnectionRequest body, HttpServletRequest req) {
        ConnectionResult r = service.testConnection(body, actor(), RequestUtil.clientIp(req));
        boolean ok = r.connected() && (r.authenticated() || body.smtpUsername() == null || body.smtpUsername().isBlank());
        // A failed probe is a normal outcome of the screen, so it is a 200 with success=false, never a 5xx.
        return new ApiResponse<>(ok, r.message(), r, null, java.time.Instant.now());
    }

    @PutMapping
    public ApiResponse<EmailSettingsStatus> save(@Valid @RequestBody SaveEmailSettingsRequest body, HttpServletRequest req) {
        EmailSettingsStatus status = service.save(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(status.enabled()
                ? "Email settings saved. System emails will be sent through this SMTP server."
                : "Email settings saved. Email sending is currently disabled.", status);
    }

    @PostMapping("/test")
    public ApiResponse<Void> test(@Valid @RequestBody(required = false) TestEmailRequest body, HttpServletRequest req) {
        String recipient = body == null ? null : body.recipient();
        return ApiResponse.message(service.sendTest(recipient, actor(), RequestUtil.clientIp(req)));
    }

    /** Performs safe, read-only DNS checks (SPF, DKIM, DMARC, MX) for the sending domain. */
    @GetMapping("/domain-check")
    public ApiResponse<com.lordsai.lsi.dto.admin.EmailSettingsDtos.DomainDnsStatus> domainCheck(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String domain,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String selector) {
        return ApiResponse.ok(service.checkDomainDns(domain, selector));
    }

    // ---- Gmail via Google OAuth 2.0 -----------------------------------------------------------
    // The matching callback is public (Google redirects the browser there with no bearer token):
    // see PublicGoogleOAuthCallbackController. These three are main-admin only, like the rest of
    // this controller, and none of them can return a token — see GoogleOAuthDtos.

    /** Safe connection information: connected?, which Gmail address, and what is still missing. */
    @GetMapping("/google")
    public ApiResponse<GoogleStatus> googleStatus() {
        return ApiResponse.ok(service.googleStatus());
    }

    /**
     * "Connect Google Account": returns the URL to send the browser to. The URL is built by the
     * server so the client id, scope and single-use state cannot be tampered with in the browser.
     */
    @GetMapping("/google/connect")
    public ApiResponse<GoogleAuthorizationUrl> googleConnect() {
        return ApiResponse.ok(new GoogleAuthorizationUrl(gmailOAuth.authorizationUrl(actor())));
    }

    /** "Disconnect Google Account": revokes the grant at Google and deletes the stored token. */
    @PostMapping("/google/disconnect")
    public ApiResponse<GoogleStatus> googleDisconnect(HttpServletRequest req) {
        String message = gmailOAuth.disconnect(actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(message, service.googleStatus());
    }

    @DeleteMapping
    public ApiResponse<EmailSettingsStatus> remove(HttpServletRequest req) {
        return ApiResponse.ok("Email settings removed.", service.remove(actor(), RequestUtil.clientIp(req)));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
