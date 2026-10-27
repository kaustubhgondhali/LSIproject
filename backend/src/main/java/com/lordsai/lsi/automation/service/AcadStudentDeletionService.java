package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.automation.repository.AcadDiscountRepository;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadRecordRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.SessionRevokeReason;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.AuditService;
import com.lordsai.lsi.service.EbookEntitlementService;
import com.lordsai.lsi.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Permanently deletes ONE academy student and everything that belongs only to them: payments,
 * receipts, discounts, attendance marks and message log. Every delete is filtered by this
 * student's id, so no other student's data is touched; batches, courses and attendance sessions
 * stay. Their ebook login loses the academy ebook; a login made only for that ebook is switched
 * off, a website account they already had is otherwise left as it is.
 */
@Service
public class AcadStudentDeletionService {

    private final AcadStudentRepository students;
    private final AcadPaymentRepository payments;
    private final AcadReceiptRepository receipts;
    private final AcadDiscountRepository discounts;
    private final AcadRecordRepository attendance;
    private final AcadCommunicationLogRepository communications;
    private final EbookEntitlementService entitlements;
    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final SessionService sessions;
    private final AuditService auditService;

    public AcadStudentDeletionService(AcadStudentRepository students, AcadPaymentRepository payments, AcadReceiptRepository receipts,
                                      AcadDiscountRepository discounts, AcadRecordRepository attendance,
                                      AcadCommunicationLogRepository communications, EbookEntitlementService entitlements,
                                      StudentProfileRepository profiles, UserRepository users, SessionService sessions,
                                      AuditService auditService) {
        this.students = students;
        this.payments = payments;
        this.receipts = receipts;
        this.discounts = discounts;
        this.attendance = attendance;
        this.communications = communications;
        this.entitlements = entitlements;
        this.profiles = profiles;
        this.users = users;
        this.sessions = sessions;
        this.auditService = auditService;
    }

    /** {@code confirmStudentId} must be the student's own Student ID, typed by the office. */
    @Transactional
    public String delete(Long id, String confirmStudentId, User actor, String ip) {
        AcadStudent s = students.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Student", id));
        if (confirmStudentId == null || !confirmStudentId.trim().equalsIgnoreCase(s.getStudentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Type the Student ID " + s.getStudentId() + " to confirm the delete.");
        }
        String code = s.getStudentId();
        String name = s.getFullName();
        long paymentCount = payments.countByStudentId(id);
        BigDecimal paid = payments.totalPaidByStudent(id);

        User login = s.getPortalUser();
        if (login != null) {
            entitlements.setAcademyEbooksBlocked(login, true, actor, ip);
            boolean ebookOnly = profiles.findByUserId(login.getId()).map(p -> p.isAcademyEbookOnly()).orElse(false);
            if (ebookOnly && login.getAccountStatus() != AccountStatus.DISABLED) {
                login.setAccountStatus(AccountStatus.DISABLED);
                users.save(login);
                sessions.revokeAll(login.getId(), SessionRevokeReason.ACCOUNT_DISABLED);
            }
        }

        var messageRows = communications.findByStudentIdOrderByCreatedAtDesc(id);
        var receiptRows = receipts.findByStudentIdOrderByIssuedAtDesc(id);
        var markRows = attendance.historyForStudent(id);
        int messages = messageRows.size(), receiptCount = receiptRows.size(), marks = markRows.size();
        communications.deleteAll(messageRows);
        communications.flush();
        receipts.deleteAll(receiptRows);
        receipts.flush();
        payments.deleteAll(payments.findByStudentIdOrderByInstallmentNoAsc(id));
        discounts.deleteAll(discounts.findByStudentIdOrderByInstallmentNoAsc(id));
        attendance.deleteAll(markRows);
        payments.flush();
        students.delete(s);
        students.flush();

        auditService.record(actor, "ACAD_STUDENT_DELETED", "AcadStudent", id,
                code + " " + name + " permanently deleted: " + paymentCount + " payment(s) of ₹" + AcadReceiptPdfService.indian(paid)
                        + ", " + receiptCount + " receipt(s), " + marks + " attendance mark(s), " + messages + " message log row(s)"
                        + (login != null ? "; ebook login " + login.getEmail() + " lost the academy ebook" : ""), ip);
        return "Student " + code + " (" + name + ") and all of their records were deleted.";
    }
}
