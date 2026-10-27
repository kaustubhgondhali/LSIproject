package com.lordsai.lsi.security;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Turns a valid bearer token into an authenticated request. A token is only honoured when
 * every one of these holds: signature and expiry are valid, the session row it names is still
 * active, the account is ACTIVE, and the token was issued at the account's current password version.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Request attribute naming why the bearer token was refused, so the 401 can tell the browser (SecurityConfig). */
    public static final String REJECTION_REASON = "lsi.auth.rejectionReason";
    /** The token's session was ended by a newer login of the same account on another device or browser. */
    public static final String SIGNED_IN_ELSEWHERE = "SIGNED_IN_ELSEWHERE";
    public static final String SIGNED_IN_ELSEWHERE_MESSAGE =
            "You were logged out because your account was signed in on another device or browser.";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final com.lordsai.lsi.service.DeviceBindingService deviceBindingService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   SessionService sessionService,
                                   UserRepository userRepository,
                                   com.lordsai.lsi.service.DeviceBindingService deviceBindingService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.deviceBindingService = deviceBindingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        resolve(header.substring(BEARER_PREFIX.length()).trim(), request).ifPresent(auth -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    auth,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + auth.role().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        chain.doFilter(request, response);
    }

    private Optional<AuthUser> resolve(String token, HttpServletRequest request) {
        Optional<Claims> parsed = jwtService.parse(token);
        if (parsed.isEmpty()) {
            return rejected(request, "token invalid or expired");
        }
        Claims claims = parsed.get();

        Optional<UserSession> session = sessionService.findActive(claims.getId());
        if (session.isEmpty()) {
            if (sessionService.replacedByNewLogin(claims.getId())) {
                request.setAttribute(REJECTION_REASON, SIGNED_IN_ELSEWHERE);
                return rejected(request, "signed in on another device or browser (a newer login replaced this session)");
            }
            return rejected(request, "login session ended (logged out, revoked or expired)");
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            return rejected(request, "malformed subject");
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive() || !user.get().getRole().isUsable()) {
            return rejected(request, "account " + userId + " missing, inactive or not allowed to sign in");
        }

        Integer tokenVersion = claims.get(JwtService.CLAIM_TOKEN_VERSION, Integer.class);
        if (tokenVersion == null || tokenVersion != user.get().getTokenVersion()) {
            return rejected(request, "password changed since this login (user " + userId + ")");
        }

        if (!session.get().getUser().getId().equals(userId)) {
            return rejected(request, "session belongs to another account");
        }

        User u = user.get();
        if (u.getRole() == Role.STUDENT && deviceBindingService.isDeviceBindingEnforced()) {
            String path = request.getRequestURI();
            String deviceIdHeader = request.getHeader("X-Device-Id");
            String deviceTokenHeader = request.getHeader("X-Device-Token");
            String sessionDevId = session.get().getDeviceId();

            if (sessionDevId != null) {
                if (!deviceBindingService.validateActiveDevice(u.getId(), sessionDevId)) {
                    return rejected(request, "device reset or revoked (user " + userId + ")");
                }
                if (path != null && path.startsWith("/api/student") && !path.endsWith("/video")) {
                    if (deviceIdHeader == null || deviceTokenHeader == null) {
                        return rejected(request, "device headers missing (user " + userId + ")");
                    }
                    if (!deviceBindingService.verifyDeviceToken(u.getId(), deviceIdHeader, deviceTokenHeader)) {
                        return rejected(request, "device credentials do not match (user " + userId + ")");
                    }
                }
            } else if (path != null && path.startsWith("/api/student") && !path.endsWith("/video")) {
                if (deviceBindingService.getStudentDevice(u.getId()).isPresent()) {
                    return rejected(request, "session not bound to the registered device (user " + userId + ")");
                }
            }
        }

        sessionService.touch(session.get());

        return Optional.of(new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), session.get().getId()));
    }

    /** Logs why a bearer token was not accepted (never the token itself), so a 401 can be explained. */
    private static Optional<AuthUser> rejected(HttpServletRequest request, String reason) {
        log.info("[AUTH] Token not accepted for {} {}: {}", request.getMethod(), request.getRequestURI(), reason);
        return Optional.empty();
    }
}
