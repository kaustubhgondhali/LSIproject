package com.lordsai.lsi.email.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Google's OAuth 2.0 <em>web server</em> flow and the Gmail send call, over plain HTTPS.
 *
 * <p>Deliberately written against Google's documented REST endpoints with the JDK's HttpClient
 * instead of pulling in the Google API client libraries: the project builds offline
 * ({@code mvn -o}) and this needs four requests in total, so a large dependency tree would cost
 * more than it saves.
 *
 * <p><b>Secrets:</b> the client secret, authorization codes, access tokens and refresh tokens are
 * passed as method arguments and request bodies only. Nothing in this class logs them, returns them
 * in an exception message, or puts them in a URL query string that could be captured by a proxy
 * log — the token and revoke calls send them in a POST body, as Google requires.
 */
@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final String GMAIL_SEND_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    /**
     * The least Google will grant that still does the job:
     * <ul>
     *   <li>{@code gmail.send} — send a message as the authorised account. It grants no ability to
     *       read, list or delete mail, unlike {@code https://mail.google.com/}.</li>
     *   <li>{@code openid email} — identity only, so the screen can display which Gmail address is
     *       connected. Google returns it inside the ID token in the same response, so no extra call
     *       and no mailbox access is involved.</li>
     * </ul>
     */
    public static final String SCOPE = "https://www.googleapis.com/auth/gmail.send openid email";

    /** The Gmail send permission specifically — checked against what Google actually granted. */
    public static final String SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final GoogleOAuthProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Never follow a redirect automatically on a token call: a redirect would replay the
            // POST body (which carries the client secret) to whatever host was named.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    /** What the admin's browser is sent to. Contains no secret — only the public client id and our state. */
    public String authorizationUrl(String state) {
        requireConfigured();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", properties.clientId());
        params.put("redirect_uri", properties.redirectUri());
        params.put("response_type", "code");
        params.put("scope", SCOPE);
        // offline + consent is what makes Google return a refresh token; without consent Google
        // omits it on a repeat authorisation and the connection would silently not survive.
        params.put("access_type", "offline");
        params.put("prompt", "consent");
        params.put("include_granted_scopes", "true");
        params.put("state", state);
        return AUTH_ENDPOINT + "?" + form(params);
    }

    /** Swaps the one-time authorization code for an access token and a refresh token. */
    public GoogleTokens exchangeCode(String code) {
        requireConfigured();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("client_id", properties.clientId());
        params.put("client_secret", properties.clientSecret());
        params.put("redirect_uri", properties.redirectUri());
        params.put("grant_type", "authorization_code");
        JsonNode json = postForm(TOKEN_ENDPOINT, params, "authorization code exchange");
        return GoogleTokens.from(json, emailFromIdToken(json.path("id_token").asText(null)));
    }

    /** Trades the stored refresh token for a fresh access token. Google does not return a new refresh token here. */
    public GoogleTokens refreshAccessToken(String refreshToken) {
        requireConfigured();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("refresh_token", refreshToken);
        params.put("client_id", properties.clientId());
        params.put("client_secret", properties.clientSecret());
        params.put("grant_type", "refresh_token");
        JsonNode json = postForm(TOKEN_ENDPOINT, params, "access token refresh");
        return GoogleTokens.from(json, null);
    }

    /**
     * Asks Google to invalidate the grant, so "Disconnect" actually withdraws the academy's access
     * rather than only forgetting it locally. A failure here is not fatal — the local token is
     * deleted either way — so it returns a flag instead of throwing.
     */
    public boolean revoke(String token) {
        if (token == null || token.isBlank() || !properties.isConfigured()) {
            return false;
        }
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(REVOKE_ENDPOINT))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token", token))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            // 200 = revoked; 400 = already invalid, which is the same end state for us.
            return res.statusCode() == 200 || res.statusCode() == 400;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[GMAIL OAUTH] Token revocation call did not complete: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Sends one already-built RFC 5322 message through the Gmail API.
     *
     * @param accessToken a currently valid access token
     * @param rawMessage  the full MIME message bytes
     */
    public void sendRaw(String accessToken, byte[] rawMessage) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "raw", Base64.getUrlEncoder().withoutPadding().encodeToString(rawMessage)));
        } catch (Exception e) {
            throw new GoogleAuthException("The message could not be prepared for the Gmail API.", false, e);
        }

        HttpResponse<String> res;
        try {
            res = http.send(
                    HttpRequest.newBuilder(URI.create(GMAIL_SEND_ENDPOINT))
                            .timeout(TIMEOUT)
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleAuthException("Sending through Gmail was interrupted.", false, e);
        } catch (Exception e) {
            throw new GoogleAuthException(
                    "Could not reach the Gmail API. Check the server's internet connection.", false, e);
        }

        if (res.statusCode() / 100 == 2) {
            return;
        }
        throw gmailError(res.statusCode(), res.body());
    }

    // ---- internals --------------------------------------------------------------------------

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new GoogleAuthException("Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID, "
                    + "GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI, then restart the backend.", false);
        }
    }

    /** POSTs a form to a Google OAuth endpoint and returns the parsed JSON, or throws a safe error. */
    private JsonNode postForm(String endpoint, Map<String, String> params, String what) {
        HttpResponse<String> res;
        try {
            res = http.send(
                    HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleAuthException("The Google " + what + " was interrupted.", false, e);
        } catch (Exception e) {
            throw new GoogleAuthException(
                    "Could not reach Google to complete the " + what + ". Check the server's internet connection.",
                    false, e);
        }

        JsonNode json;
        try {
            json = mapper.readTree(res.body() == null ? "{}" : res.body());
        } catch (Exception e) {
            throw new GoogleAuthException("Google returned an unreadable response to the " + what + ".", false, e);
        }
        if (res.statusCode() / 100 == 2) {
            return json;
        }

        // Only Google's short error *code* is used. error_description can echo request content,
        // so it is never surfaced or logged.
        String error = json.path("error").asText("");
        log.warn("[GMAIL OAUTH] Google rejected the {} (HTTP {}, error={})", what, res.statusCode(), error);
        throw switch (error) {
            case "invalid_grant" -> GoogleAuthException.revoked();
            case "invalid_client", "unauthorized_client" -> new GoogleAuthException(
                    "Google rejected this application's OAuth client. Check GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET "
                            + "against the OAuth client in Google Cloud.", false);
            case "redirect_uri_mismatch" -> new GoogleAuthException(
                    "The redirect URI does not match the one registered in Google Cloud. Add this application's "
                            + "callback URL to the OAuth client's Authorized redirect URIs.", false);
            case "invalid_scope" -> new GoogleAuthException(
                    "Google rejected the requested permission. Make sure the Gmail API is enabled and the "
                            + "gmail.send scope is allowed on the OAuth consent screen.", false);
            case "access_denied" -> new GoogleAuthException(
                    "Access was denied on the Google consent screen.", false);
            default -> new GoogleAuthException(
                    "Google refused the " + what + " (HTTP " + res.statusCode() + ").", false);
        };
    }

    /** Turns a Gmail API error response into something an administrator can act on. */
    private GoogleAuthException gmailError(int status, String body) {
        String reason = "";
        try {
            reason = mapper.readTree(body == null ? "{}" : body).path("error").path("status").asText("");
        } catch (Exception ignored) {
            // The status code alone is enough; the body is never surfaced.
        }
        log.warn("[GMAIL API] Send rejected (HTTP {}, status={})", status, reason);
        if (status == 401) {
            return GoogleAuthException.revoked();
        }
        if (status == 403) {
            return new GoogleAuthException("Gmail refused to send this message. Confirm the Gmail API is enabled in "
                    + "Google Cloud and that the connected account granted permission to send mail.", false);
        }
        if (status == 429 || status / 100 == 5) {
            return new GoogleAuthException("Gmail is temporarily unavailable or the sending limit was reached. "
                    + "Please try again shortly.", false);
        }
        if (status == 400) {
            return new GoogleAuthException("Gmail rejected the message — check the recipient address.", false);
        }
        return new GoogleAuthException("Gmail refused to send the message (HTTP " + status + ").", false);
    }

    /**
     * Reads the {@code email} claim out of Google's ID token. The token arrives over TLS directly
     * from Google's token endpoint in response to our authenticated request, so the claims are
     * trusted without a second signature check; it is used only to display which account is
     * connected, never for authorisation inside this application.
     */
    private String emailFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String email = claims.path("email").asText(null);
            return email == null || email.isBlank() ? null : email.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            // A missing address is not fatal — the connection still works, the screen just cannot name it.
            log.warn("[GMAIL OAUTH] Could not read the account address from Google's ID token.");
            return null;
        }
    }

    private static String form(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
