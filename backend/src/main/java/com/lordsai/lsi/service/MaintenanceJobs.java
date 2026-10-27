package com.lordsai.lsi.service;

import com.lordsai.lsi.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final UserSessionRepository sessionRepository;

    public MaintenanceJobs(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Marks expired sessions inactive so the single-device check and admin views stay accurate. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public void expireStaleSessions() {
        int n = sessionRepository.expireStaleSessions(Instant.now());
        if (n > 0) {
            log.info("Expired {} stale session(s)", n);
        }
    }
}
