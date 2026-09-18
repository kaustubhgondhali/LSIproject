package com.lordsai.lsi.security;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Guarantees a Main Admin (role ADMIN) exists at startup, once, idempotently.
 *
 * <p>Identity comes from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD when set; otherwise
 * the built-in development default below is used, so a fresh local database is usable
 * immediately with User ID {@code admin} and password {@code Admin@123}. ("admin" resolves
 * through the normal username shortcut: the part of the email before "@".)
 *
 * <p>An existing account is never modified, and nothing is seeded while any ADMIN already
 * exists, so a well-known default can never appear beside real administrators. Only the
 * BCrypt hash is stored; the password is never logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /** Development default. Override on any real server with BOOTSTRAP_ADMIN_EMAIL / _PASSWORD. */
    static final String DEFAULT_ADMIN_EMAIL = "admin@lordsaiinvestment.com";
    static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String bootstrapName;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService,
                          @Value("${lsi.bootstrap-admin.email:}") String bootstrapEmail,
                          @Value("${lsi.bootstrap-admin.password:}") String bootstrapPassword,
                          @Value("${lsi.bootstrap-admin.name:Academy Administrator}") String bootstrapName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = !bootstrapEmail.isBlank() && !bootstrapPassword.isBlank();
        String email = (configured ? bootstrapEmail : DEFAULT_ADMIN_EMAIL).trim().toLowerCase();
        String password = configured ? bootstrapPassword : DEFAULT_ADMIN_PASSWORD;

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            log.info("[MAIN ADMIN] Default Main Admin already exists ({}). Left unchanged.", email);
            return;
        }
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            log.info("[MAIN ADMIN] An ADMIN account already exists; default '{}' was not seeded.", email);
            return;
        }
        if (password.length() < 8) {
            log.error("[MAIN ADMIN] BOOTSTRAP_ADMIN_PASSWORD must be at least 8 characters. Admin was NOT created.");
            return;
        }

        User admin = new User();
        admin.setFullName(bootstrapName);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin = userRepository.save(admin);

        auditService.record(admin, "ADMIN_BOOTSTRAPPED", "User", admin.getId(),
                configured ? "Initial administrator account created from environment configuration"
                           : "Default development administrator account created", null);

        log.info("[MAIN ADMIN] Default Main Admin created successfully ({}, role {}).", admin.getEmail(), Role.ADMIN);
        if (!configured) {
            log.warn("[MAIN ADMIN] Using the built-in development password. Log in as 'admin', change the "
                    + "password, or set BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD before deploying.");
        }
    }
}
