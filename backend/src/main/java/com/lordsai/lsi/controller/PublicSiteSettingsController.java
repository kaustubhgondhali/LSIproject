package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.service.SiteSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public website settings the static pages need (js/site-whatsapp.js). */
@RestController
@RequestMapping("/api/public")
public class PublicSiteSettingsController {

    private final SiteSettingsService settings;

    public PublicSiteSettingsController(SiteSettingsService settings) {
        this.settings = settings;
    }

    /** The number every WhatsApp link and enquiry form on the website opens a chat with. */
    @GetMapping("/site-contact")
    public ApiResponse<Map<String, String>> siteContact() {
        return ApiResponse.ok(Map.of("whatsappNumber", settings.websiteWhatsAppNumber()));
    }
}
