package com.lordsai.lsi.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Official WhatsApp Business Platform — Cloud API (Meta Graph API). Credentials arrive per call
 * as {@link WhatsAppSettings}, resolved centrally by {@link WhatsAppMessageService} (the admin
 * configuration saved in Main Admin -> Settings, or the WHATSAPP_* environment variables). The
 * access token is used only in the Authorization header and never logged or returned.
 *
 * <p>Note: Meta only accepts free-form text/document messages inside an open 24-hour customer
 * service window; outside it a pre-approved message template is required. Failures from the API
 * are reported back verbatim (minus secrets) so the admin can see why a message was rejected.
 */
@Component
public class MetaCloudWhatsAppProvider implements WhatsAppProvider {

    public static final String NAME = "META_CLOUD";
    private static final Logger log = LoggerFactory.getLogger(MetaCloudWhatsAppProvider.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MetaCloudWhatsAppProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supportsDocuments() {
        return true;
    }

    @Override
    public WhatsAppDelivery sendText(WhatsAppSettings s, String toE164, String text) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", toE164,
                "type", "text",
                "text", Map.of("preview_url", false, "body", text));
        return postMessage(s, body);
    }

    @Override
    public WhatsAppDelivery sendDocument(WhatsAppSettings s, String toE164, String caption, String filename,
                                         String contentType, byte[] content) {
        String mediaId;
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("messaging_product", "whatsapp");
            form.add("type", contentType);
            form.add("file", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            String response = restClient.post()
                    .uri(url(s, "/media"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            mediaId = objectMapper.readTree(response == null ? "{}" : response).path("id").asText(null);
            if (mediaId == null) {
                return WhatsAppDelivery.failed("WhatsApp media upload returned no media id.", NAME);
            }
        } catch (RestClientResponseException e) {
            return WhatsAppDelivery.failed(describe(e), NAME);
        } catch (Exception e) {
            return WhatsAppDelivery.failed("WhatsApp media upload failed: " + e.getClass().getSimpleName(), NAME);
        }
        Map<String, Object> doc = caption == null || caption.isBlank()
                ? Map.of("id", mediaId, "filename", filename)
                : Map.of("id", mediaId, "filename", filename, "caption", caption);
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", toE164,
                "type", "document",
                "document", doc);
        return postMessage(s, body);
    }

    /** GET /{phoneNumberId} with the token: proves both the token and the phone number id without sending anything. */
    @Override
    public ValidationResult verify(WhatsAppSettings s) {
        if (s == null || !s.complete()) {
            return ValidationResult.failure("provider, access token and phone number ID are all required");
        }
        try {
            String response = restClient.get()
                    .uri(url(s, "?fields=display_phone_number,verified_name,quality_rating"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String display = root.path("display_phone_number").asText(null);
            String verifiedName = root.path("verified_name").asText(null);
            if (display == null && verifiedName == null) {
                return ValidationResult.failure("the phone number ID was not recognised");
            }
            return ValidationResult.success(display == null ? verifiedName : display + (verifiedName == null ? "" : " · " + verifiedName));
        } catch (RestClientResponseException e) {
            String reason = describe(e);
            log.warn("[WHATSAPP] Credential check rejected by Meta Cloud API: {}", reason);
            return ValidationResult.failure(reason);
        } catch (Exception e) {
            log.warn("[WHATSAPP] Credential check failed: {}", e.getClass().getSimpleName());
            return ValidationResult.failure("WhatsApp API unreachable: " + e.getClass().getSimpleName());
        }
    }

    private WhatsAppDelivery postMessage(WhatsAppSettings s, Map<String, Object> body) {
        try {
            String response = restClient.post()
                    .uri(url(s, "/messages"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String id = root.path("messages").path(0).path("id").asText(null);
            if (id == null) {
                return WhatsAppDelivery.failed("WhatsApp API accepted the request but returned no message id.", NAME);
            }
            return WhatsAppDelivery.sent(id, NAME);
        } catch (RestClientResponseException e) {
            String reason = describe(e);
            log.warn("[WHATSAPP] Rejected by Meta Cloud API: {}", reason);
            return WhatsAppDelivery.failed(reason, NAME);
        } catch (Exception e) {
            log.warn("[WHATSAPP] Send failed: {}", e.getClass().getSimpleName());
            return WhatsAppDelivery.failed("WhatsApp API unreachable: " + e.getClass().getSimpleName(), NAME);
        }
    }

    private static String url(WhatsAppSettings s, String suffix) {
        String base = (s.apiUrl() == null || s.apiUrl().isBlank() ? "https://graph.facebook.com" : s.apiUrl()).replaceAll("/+$", "");
        String version = s.apiVersion() == null || s.apiVersion().isBlank() ? "v20.0" : s.apiVersion();
        return base + "/" + version + "/" + s.phoneNumberId() + suffix;
    }

    /** Meta's error payload carries a human message; never echo our own token back. */
    private String describe(RestClientResponseException e) {
        try {
            JsonNode err = objectMapper.readTree(e.getResponseBodyAsString()).path("error");
            String msg = err.path("message").asText("");
            String details = err.path("error_data").path("details").asText("");
            String text = (msg + (details.isBlank() ? "" : " — " + details)).trim();
            if (!text.isBlank()) {
                return "WhatsApp API error " + e.getStatusCode().value() + ": " + text;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "WhatsApp API error " + e.getStatusCode().value() + ".";
    }
}
