package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.SaveWhatsAppRequest;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.TestMessageRequest;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.WhatsAppStatus;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.WhatsAppTestResult;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.SiteSettingsService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.service.WhatsAppConfigService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Main Admin -> Settings -> WhatsApp Settings. Main-admin only (enforced by the /api/admin/**
 * rule in SecurityConfig). Never returns the access token.
 */
@RestController
@RequestMapping("/api/admin/whatsapp")
public class AdminWhatsAppController {

    private final WhatsAppConfigService service;
    private final UserService userService;
    private final SiteSettingsService siteSettings;

    public AdminWhatsAppController(WhatsAppConfigService service, UserService userService, SiteSettingsService siteSettings) {
        this.service = service;
        this.userService = userService;
        this.siteSettings = siteSettings;
    }

    public record WebsiteNumberRequest(@NotBlank @Size(max = 20) String number) {
    }

    /** The website WhatsApp number: receives enquiry forms and every "Chat on WhatsApp" click. */
    @GetMapping("/website-number")
    public ApiResponse<Map<String, String>> websiteNumber() {
        return ApiResponse.ok(Map.of("number", siteSettings.websiteWhatsAppNumber()));
    }

    @PutMapping("/website-number")
    public ApiResponse<Map<String, String>> saveWebsiteNumber(@Valid @RequestBody WebsiteNumberRequest body, HttpServletRequest req) {
        String number = siteSettings.updateWebsiteWhatsAppNumber(body.number(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Website WhatsApp number saved.", Map.of("number", number));
    }

    @GetMapping
    public ApiResponse<WhatsAppStatus> status() {
        return ApiResponse.ok(service.status());
    }

    /** Create or update. Credentials are verified with the provider before WhatsApp is enabled. */
    @PutMapping
    public ApiResponse<WhatsAppStatus> save(@Valid @RequestBody SaveWhatsAppRequest body, HttpServletRequest req) {
        return ApiResponse.ok("WhatsApp configuration verified and enabled.", service.save(body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/disable")
    public ApiResponse<WhatsAppStatus> disable(HttpServletRequest req) {
        return ApiResponse.ok("WhatsApp disabled.", service.disable(actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/enable")
    public ApiResponse<WhatsAppStatus> enable(HttpServletRequest req) {
        return ApiResponse.ok("WhatsApp re-enabled.", service.enable(actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping
    public ApiResponse<WhatsAppStatus> remove(HttpServletRequest req) {
        return ApiResponse.ok("WhatsApp configuration removed.", service.remove(actor(), RequestUtil.clientIp(req)));
    }

    /** Read-only credential check; a failed check is a 400 with the friendly reason (no secrets). */
    @PostMapping("/test-connection")
    public ApiResponse<WhatsAppTestResult> testConnection(HttpServletRequest req) {
        WhatsAppTestResult r = service.testConnection(actor(), RequestUtil.clientIp(req));
        if (!r.ok()) {
            throw new com.lordsai.lsi.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, r.message());
        }
        return ApiResponse.ok(r.message(), r);
    }

    @PostMapping("/test-message")
    public ApiResponse<WhatsAppTestResult> testMessage(@Valid @RequestBody TestMessageRequest body, HttpServletRequest req) {
        WhatsAppTestResult r = service.sendTest(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(r.message(), r);
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
