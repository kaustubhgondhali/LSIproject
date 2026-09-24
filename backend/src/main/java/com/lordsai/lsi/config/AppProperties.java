package com.lordsai.lsi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application settings bound from configuration/environment variables.
 * No secret ever has a usable default here — secrets come from the environment.
 */
@ConfigurationProperties(prefix = "lsi")
public record AppProperties(
        String publicBaseUrl,
        Cors cors,
        Jwt jwt,
        Storage storage,
        Support support,
        Mail mail,
        WhatsApp whatsapp
) {

    /**
     * Official WhatsApp Business (Cloud API) provider settings. All values come from the
     * environment; nothing here has a usable default and secrets are never returned by any API.
     */
    public record WhatsApp(String provider, String apiUrl, String accessToken, String phoneNumberId,
                           String businessAccountId, String apiVersion) {
        public boolean configured() {
            return provider != null && !provider.isBlank() && !"NONE".equalsIgnoreCase(provider)
                    && accessToken != null && !accessToken.isBlank()
                    && phoneNumberId != null && !phoneNumberId.isBlank();
        }
    }

    public record Mail(boolean enabled, String from, String replyTo, String sendingDomain, String dkimSelector) {
        public Mail(boolean enabled, String from) {
            this(enabled, from, null, null, "default");
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(
            String secret,
            long accessTokenMinutes,
            long refreshTokenDays,
            String issuer
    ) {
    }

    public record Storage(
            String basePath,
            long maxVideoSizeMb,
            long maxDocumentSizeMb,
            Long maxEbookSizeMb
    ) {
        /** Ebook PDFs may be larger than lesson handouts; default 100 MB. */
        public long ebookLimitMb() {
            return maxEbookSizeMb == null || maxEbookSizeMb <= 0 ? 100 : maxEbookSizeMb;
        }
    }

    public record Support(String email, String phone) {
    }
}
