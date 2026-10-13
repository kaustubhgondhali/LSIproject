package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.EbookActivatedEvent;
import com.lordsai.lsi.service.EbookEntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An ebook the Main Admin puts on sale later is given free to every academy ebook login at once —
 * as blocked for a student whose ebook the office blocked — so academy students never pay for an
 * ebook. Runs after the ebook is saved, in its own transaction: a problem here is only logged and
 * can never undo or fail the Main Admin's upload.
 */
@Component
public class AcadNewEbookGrant {

    private static final Logger log = LoggerFactory.getLogger(AcadNewEbookGrant.class);

    private final AcadStudentRepository students;
    private final UserRepository users;
    private final EbookEntitlementService entitlements;
    private final TransactionTemplate tx;

    public AcadNewEbookGrant(AcadStudentRepository students, UserRepository users, EbookEntitlementService entitlements,
                             PlatformTransactionManager transactionManager) {
        this.students = students;
        this.users = users;
        this.entitlements = entitlements;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEbookActivated(EbookActivatedEvent event) {
        try {
            Integer logins = tx.execute(status -> giveToAll(
                    event.actor() == null ? null : users.findById(event.actor().getId()).orElse(null), event.ip()));
            if (logins != null && logins > 0) {
                log.info("[EBOOK] Ebook {} given free to the academy ebook logins ({} checked)", event.ebookId(), logins);
            }
        } catch (RuntimeException e) {
            log.error("[EBOOK] Ebook {} could not be given to the academy ebook logins: {}", event.ebookId(), e.getMessage());
        }
    }

    /** Must run inside a transaction. Gives each academy login every active ebook it is missing; returns how many logins were checked. */
    public int giveToAll(User actor, String ip) {
        int logins = 0;
        for (AcadStudent s : students.findByPortalUserIsNotNull()) {
            entitlements.grantAcademyEbooks(s.getPortalUser(), s.isEbookBlocked(), actor, ip);
            logins++;
        }
        return logins;
    }
}
