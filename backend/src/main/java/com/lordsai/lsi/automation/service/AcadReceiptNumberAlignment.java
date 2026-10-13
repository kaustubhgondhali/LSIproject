package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Receipt numbers follow the Student ID (LSA/2026/0025 -> LSR/2026/0025). Safe to run on every
 * start: a student whose receipt number does not match — every student numbered before this rule,
 * when the receipt number came from its own counter — gets the matching number, on the student
 * and on every one of their receipts, and the old number is written to the audit log. A student
 * with no payment yet is numbered at the first payment. Never stops the server from starting.
 */
@Component
public class AcadReceiptNumberAlignment implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AcadReceiptNumberAlignment.class);

    private final AcadStudentRepository students;
    private final AcadReceiptRepository receipts;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public AcadReceiptNumberAlignment(AcadStudentRepository students, AcadReceiptRepository receipts, AuditService auditService,
                                      PlatformTransactionManager transactionManager) {
        this.students = students;
        this.receipts = receipts;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer changed = tx.execute(status -> align());
            if (changed != null && changed > 0) {
                log.info("[RECEIPT NO] {} student(s) now have receipt numbers matching their Student ID", changed);
            }
        } catch (RuntimeException e) {
            log.error("[RECEIPT NO] Receipt numbers could not be matched to Student IDs: {}", e.getMessage());
        }
    }

    /** Must run inside a transaction. Returns how many students were renumbered. */
    public int align() {
        Map<Long, List<AcadReceipt>> byStudent = receipts.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getId()));
        List<AcadStudent> all = students.findAll();
        Map<AcadStudent, String> wanted = new HashMap<>();
        Set<String> taken = new HashSet<>();
        List<AcadStudent> toChange = new ArrayList<>();
        for (AcadStudent s : all) {
            List<AcadReceipt> own = byStudent.getOrDefault(s.getId(), List.of());
            if (s.getFeeReceiptNo() == null && own.isEmpty()) {
                continue;   // no payment yet
            }
            String want = AcadPaymentService.receiptNoFor(s.getStudentId());
            boolean matches = want.equals(s.getFeeReceiptNo()) && own.stream().allMatch(r -> want.equals(r.getReceiptNo()));
            if (matches) {
                taken.add(want);
            } else {
                wanted.put(s, want);
                toChange.add(s);
            }
        }
        // Two Student IDs that would give the same receipt number (only possible with unusual
        // office-chosen prefixes) are left as they are rather than clashing.
        List<AcadStudent> safe = new ArrayList<>();
        for (AcadStudent s : toChange) {
            if (taken.add(wanted.get(s))) {
                safe.add(s);
            } else {
                log.warn("[RECEIPT NO] {} keeps receipt number {}: {} is already used", s.getStudentId(), s.getFeeReceiptNo(), wanted.get(s));
            }
        }
        if (safe.isEmpty()) {
            return 0;
        }
        Map<Long, String> oldNumbers = new HashMap<>();
        for (AcadStudent s : safe) {
            List<AcadReceipt> own = byStudent.getOrDefault(s.getId(), List.of());
            oldNumbers.put(s.getId(), s.getFeeReceiptNo() != null ? s.getFeeReceiptNo() : own.get(0).getReceiptNo());
            s.setFeeReceiptNo(null);   // pass 1: free the old numbers (the unique key allows any number of empty ones)
        }
        students.saveAllAndFlush(safe);
        List<AcadReceipt> changedReceipts = new ArrayList<>();
        for (AcadStudent s : safe) {
            String want = wanted.get(s);
            s.setFeeReceiptNo(want);   // pass 2: the matching numbers, unique because Student IDs are
            for (AcadReceipt r : byStudent.getOrDefault(s.getId(), List.of())) {
                r.setReceiptNo(want);
                changedReceipts.add(r);
            }
            auditService.record(null, "ACAD_RECEIPT_NUMBER_CHANGED", "AcadStudent", s.getId(),
                    s.getStudentId() + ": receipt number " + oldNumbers.get(s.getId()) + " -> " + want
                            + " (receipt numbers now follow the Student ID)", null);
        }
        students.saveAllAndFlush(safe);
        receipts.saveAllAndFlush(changedReceipts);
        return safe.size();
    }
}
