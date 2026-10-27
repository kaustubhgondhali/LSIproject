package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.SiteSetting;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.SiteSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Website settings the Main Admin can change. The website WhatsApp number is where every
 * "Chat on WhatsApp" link and the enquiry forms on the public website open a chat.
 */
@Service
public class SiteSettingsService {

    public static final String WEBSITE_WHATSAPP_NUMBER = "WEBSITE_WHATSAPP_NUMBER";
    /** 99870 49070 — used until the Main Admin saves another number. */
    public static final String DEFAULT_WEBSITE_WHATSAPP = "919987049070";

    private final SiteSettingRepository repository;
    private final AuditService auditService;

    public SiteSettingsService(SiteSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** International digits without "+", e.g. "919987049070". */
    @Transactional(readOnly = true)
    public String websiteWhatsAppNumber() {
        return repository.findById(WEBSITE_WHATSAPP_NUMBER).map(SiteSetting::getValue)
                .filter(v -> v.matches("\\d{11,15}"))
                .orElse(DEFAULT_WEBSITE_WHATSAPP);
    }

    @Transactional
    public String updateWebsiteWhatsAppNumber(String input, User actor, String ip) {
        String number = normalise(input);
        SiteSetting setting = repository.findById(WEBSITE_WHATSAPP_NUMBER).orElseGet(() -> {
            SiteSetting s = new SiteSetting();
            s.setKey(WEBSITE_WHATSAPP_NUMBER);
            return s;
        });
        String previous = setting.getValue();
        setting.setValue(number);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actor);
        repository.save(setting);
        auditService.record(actor, "WEBSITE_WHATSAPP_NUMBER_UPDATED", "SiteSetting", null,
                "Website WhatsApp number " + (previous == null ? "set" : "changed from +" + previous) + " to +" + number, ip);
        return number;
    }

    /** India first: 10 digits get 91; a leading 0 / 00 / + is dropped. Same rule as js/whatsapp-chat.js. */
    static String normalise(String input) {
        String d = input == null ? "" : input.replaceAll("\\D", "");
        if (d.startsWith("00")) {
            d = d.substring(2);
        }
        if (d.length() == 11 && d.startsWith("0")) {
            d = d.substring(1);
        }
        if (d.length() == 10) {
            d = "91" + d;
        }
        if (!d.matches("\\d{11,15}") || (d.startsWith("91") && d.length() == 12 && !d.substring(2).matches("[6-9]\\d{9}"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Enter a valid WhatsApp number: 10-digit Indian mobile, or with country code, e.g. 9987049070 or 919987049070.");
        }
        return d;
    }
}
