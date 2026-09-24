package com.lordsai.lsi.controller;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.email.google.GmailOAuthService;
import com.lordsai.lsi.email.google.GoogleAuthException;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * Where Google sends the administrator's browser after the consent screen.
 *
 * <p>This endpoint has to be public — it is a plain top-level navigation from Google's servers,
 * so there is no Authorization header to authenticate it with. It is safe because it authenticates
 * the <em>flow</em> rather than the request: the {@code state} parameter is a single-use value this
 * server signed when the admin clicked Connect, and nothing happens unless it verifies. An attacker
 * who calls this URL directly, replays an old one, or supplies their own authorization code cannot
 * produce a valid state and is simply bounced back with an error.
 *
 * <p>Nothing sensitive travels onward: the authorization code is consumed here and the browser is
 * redirected to the configured admin URL with a one-word outcome — never a token, never a code, and
 * never a URL taken from the request.
 */
@RestController
@RequestMapping("/api/public/email/google")
public class PublicGoogleOAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PublicGoogleOAuthCallbackController.class);

    private final GmailOAuthService gmailOAuth;
    private final UserService userService;
    private final AppProperties properties;

    public PublicGoogleOAuthCallbackController(GmailOAuthService gmailOAuth, UserService userService,
                                               AppProperties properties) {
        this.gmailOAuth = gmailOAuth;
        this.userService = userService;
        this.properties = properties;
    }

    /**
     * @param code  Google's one-time authorization code — exchanged immediately, never stored or logged
     * @param state the signed single-use value issued when the admin clicked Connect
     * @param error set by Google when the admin refused consent
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest request) {
        if (error != null && !error.isBlank()) {
            // "access_denied" is the normal outcome of clicking Cancel on the consent screen.
            log.info("[GMAIL OAUTH] Authorisation was not granted (Google reported: {})", safe(error));
            return redirect("denied");
        }

        // The state is checked before the code is touched: an unverified callback must not cause
        // a token exchange, which is what makes a forged or replayed callback harmless.
        Optional<Long> adminId = gmailOAuth.consumeState(state);
        if (adminId.isEmpty()) {
            log.warn("[GMAIL OAUTH] Callback rejected: the state value was missing, expired, already used or invalid.");
            return redirect("invalid_state");
        }
        if (code == null || code.isBlank()) {
            return redirect("invalid_callback");
        }

        try {
            User actor = userService.requireUser(adminId.get());
            gmailOAuth.completeConnection(code, actor, RequestUtil.clientIp(request));
            return redirect("connected");
        } catch (GoogleAuthException e) {
            // e.getMessage() is written to be safe, but it is not passed through the URL either:
            // the screen re-reads the real status from the authenticated API instead.
            log.warn("[GMAIL OAUTH] Could not complete the connection: {}", e.getMessage());
            return redirect("failed");
        } catch (RuntimeException e) {
            log.error("[GMAIL OAUTH] Unexpected failure completing the connection ({})", e.getClass().getSimpleName());
            return redirect("failed");
        }
    }

    /**
     * Back to the admin screen. The destination comes from this server's own configuration, never
     * from a request parameter, so the callback cannot be turned into an open redirect.
     */
    private ResponseEntity<Void> redirect(String outcome) {
        String base = properties.publicBaseUrl().replaceAll("/+$", "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(base + "/admin-dashboard.html?google=" + outcome + "#email"))
                .build();
    }

    /** Google's error codes are short and fixed; this stops anything else reaching the log. */
    private static String safe(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9_\\-]", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
