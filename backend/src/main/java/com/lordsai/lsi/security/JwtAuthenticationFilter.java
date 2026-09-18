package com.lordsai.lsi.security;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   SessionService sessionService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
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

        resolve(header.substring(BEARER_PREFIX.length()).trim()).ifPresent(auth -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    auth,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + auth.role().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        chain.doFilter(request, response);
    }

    private Optional<AuthUser> resolve(String token) {
        Optional<Claims> parsed = jwtService.parse(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();

        Optional<UserSession> session = sessionService.findActive(claims.getId());
        if (session.isEmpty()) {
            return Optional.empty();
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive() || !user.get().getRole().isUsable()) {
            return Optional.empty();
        }

        Integer tokenVersion = claims.get(JwtService.CLAIM_TOKEN_VERSION, Integer.class);
        if (tokenVersion == null || tokenVersion != user.get().getTokenVersion()) {
            return Optional.empty();
        }

        if (!session.get().getUser().getId().equals(userId)) {
            return Optional.empty();
        }

        sessionService.touch(session.get());

        User u = user.get();
        return Optional.of(new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), session.get().getId()));
    }
}
