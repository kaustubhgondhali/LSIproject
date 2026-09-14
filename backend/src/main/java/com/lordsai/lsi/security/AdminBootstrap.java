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
 * Creates the very first ADMIN account from environment variables, once, only when no admin
 * exists yet. No password is ever stored in source or migrations. After the first login,
 * change the password and remove BOOTSTRAP_ADMIN_* from the environment.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

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
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            log.warn("No ADMIN account exists and BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD "
                    + "are not set. The admin portal will be unusable until they are provided.");
            return;
        }
        if (bootstrapPassword.length() < 8) {
            log.error("BOOTSTRAP_ADMIN_PASSWORD must be at least 8 characters. Admin was NOT created.");
            return;
        }

        User admin = new User();
        admin.setFullName(bootstrapName);
        admin.setEmail(bootstrapEmail.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setRole(Role.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin = userRepository.save(admin);

        auditService.record(admin, "ADMIN_BOOTSTRAPPED", "User", admin.getId(),
                "Initial administrator account created from environment configuration", null);
        log.warn("Initial ADMIN account created for {}. Change the password after first login "
                + "and remove BOOTSTRAP_ADMIN_* from the environment.", admin.getEmail());
    }
}
