package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.entity.AccountToken;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.repository.AccountTokenRepository;
import com.lordsai.lsi.util.TokenUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Issues and validates the one-time links used for first-time password setup and password reset. */
@Service
public class AccountTokenService {

    private static final Duration SETUP_TTL = Duration.ofHours(48);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final AccountTokenRepository tokenRepository;
    private final AppProperties properties;

    public AccountTokenService(AccountTokenRepository tokenRepository, AppProperties properties) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
    }

    /** Creates a fresh token (invalidating any earlier one of the same purpose) and returns the full link. */
    @Transactional
    public String issueLink(User user, TokenPurpose purpose) {
        tokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);

        String raw = TokenUtil.generateOpaqueToken();
        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setTokenHash(TokenUtil.sha256Hex(raw));
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.now().plus(purpose == TokenPurpose.ACCOUNT_SETUP ? SETUP_TTL : RESET_TTL));
        tokenRepository.save(token);

        // One page serves both flows; it asks /auth/token-check for the purpose and adapts its copy.
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/set-password.html?token=" + raw;
    }

    @Transactional(readOnly = true)
    public Optional<AccountToken> findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(TokenUtil.sha256Hex(rawToken))
                .filter(AccountToken::isUsable);
    }

    @Transactional
    public void markUsed(AccountToken token) {
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
    }
}
