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
 * Guarantees the academy office login (role AUTOMATION_ADMIN) exists at startup, once.
 * User ID {@code automation} resolves through the normal username shortcut (email local part).
 * Only the BCrypt hash is stored; the password is never logged. Override the default with
 * BOOTSTRAP_AUTOMATION_PASSWORD before deploying.
 */
@Component
public class AutomationAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AutomationAdminBootstrap.class);

    static final String DEFAULT_EMAIL = "automation@lordsaiinvestment.com";
    static final String DEFAULT_PASSWORD = "Automation@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String password;

    public AutomationAdminBootstrap(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditService auditService,
                                    @Value("${lsi.bootstrap-automation.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmailIgnoreCase(DEFAULT_EMAIL).isPresent()) {
            log.info("[AUTOMATION ADMIN] Account already exists ({}). Left unchanged.", DEFAULT_EMAIL);
            return;
        }
        if (userRepository.countByRole(Role.AUTOMATION_ADMIN) > 0) {
            log.info("[AUTOMATION ADMIN] Another AUTOMATION_ADMIN account exists; default 'automation' was not seeded.");
            return;
        }
        boolean configured = password != null && !password.isBlank();
        User u = new User();
        u.setFullName("Automation Admin");
        u.setEmail(DEFAULT_EMAIL);
        u.setPasswordHash(passwordEncoder.encode(configured ? password : DEFAULT_PASSWORD));
        u.setRole(Role.AUTOMATION_ADMIN);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u = userRepository.save(u);
        auditService.record(u, "AUTOMATION_ADMIN_BOOTSTRAPPED", "User", u.getId(),
                configured ? "Automation Admin account created from environment configuration"
                           : "Default Automation Admin account created", null);
        log.info("[AUTOMATION ADMIN] Account created successfully ({}, role {}).", u.getEmail(), Role.AUTOMATION_ADMIN);
        if (!configured) {
            log.warn("[AUTOMATION ADMIN] Using the built-in development password. Log in as 'automation' and change it, "
                    + "or set BOOTSTRAP_AUTOMATION_PASSWORD before deploying.");
        }
    }
}
