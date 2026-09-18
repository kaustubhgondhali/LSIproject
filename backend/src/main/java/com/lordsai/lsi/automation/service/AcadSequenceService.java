package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadSequence;
import com.lordsai.lsi.automation.repository.AcadSequenceRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the academy's business numbers, unique under concurrent use:
 *   students  LSA/YYYY/0001   (the workbook's own convention)
 *   receipts  LSR/YYYY/0001
 *   payments  PAY/YYYY/00001
 */
@Service
public class AcadSequenceService {

    public static final String STUDENT = "STUDENT";
    public static final String RECEIPT = "RECEIPT";
    public static final String PAYMENT = "PAYMENT";

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Pattern STUDENT_ID = Pattern.compile("^LSA/?(\\d{4})/(\\d{1,6})$", Pattern.CASE_INSENSITIVE);

    private final AcadSequenceRepository sequences;
    private final AcadStudentRepository students;
    private final AuditService auditService;

    public AcadSequenceService(AcadSequenceRepository sequences, AcadStudentRepository students, AuditService auditService) {
        this.sequences = sequences;
        this.students = students;
        this.auditService = auditService;
    }

    /** Must run inside the transaction that creates the record so the row lock lasts until commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String kind) {
        int year = LocalDate.now(INDIA).getYear();
        return next(kind, year);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String kind, int year) {
        AcadSequence seq = sequences.findForUpdate(kind, year)
                .orElseGet(() -> sequences.saveAndFlush(new AcadSequence(kind, year)));
        int n = seq.getLastNumber() + 1;
        if (STUDENT.equals(kind)) {
            // Never collide with IDs that were imported from the workbook (or typed in) ahead of the counter.
            n = Math.max(n, highestExistingStudentNumber(year) + 1);
        }
        seq.setLastNumber(n);
        sequences.save(seq);
        return format(kind, year, n);
    }

    /** A preview for the form ("your next Student ID will be …"); nothing is reserved. */
    @Transactional(readOnly = true)
    public String peek(String kind) {
        int year = LocalDate.now(INDIA).getYear();
        int n = sequences.findByKindAndYear(kind, year).map(AcadSequence::getLastNumber).orElse(0) + 1;
        if (STUDENT.equals(kind)) {
            n = Math.max(n, highestExistingStudentNumber(year) + 1);
        }
        return format(kind, year, n);
    }

    // ---- Automation Admin Settings: Student ID series -------------------------------------

    /**
     * The current state of the Student ID series for the running year: the next number that
     * will actually be issued (already accounting for the existing-ID safety net below) and the
     * highest number already in use, so the admin UI can show both and validate before saving.
     */
    public record StudentIdSeriesInfo(int year, int nextNumber, String nextStudentId, int highestExistingNumber,
                                      String highestExistingStudentId) {
    }

    @Transactional(readOnly = true)
    public StudentIdSeriesInfo studentIdSeriesInfo() {
        int year = LocalDate.now(INDIA).getYear();
        int highest = highestExistingStudentNumber(year);
        int configured = sequences.findByKindAndYear(STUDENT, year).map(AcadSequence::getLastNumber).orElse(0);
        int next = Math.max(configured, highest) + 1;
        return new StudentIdSeriesInfo(year, next, format(STUDENT, year, next), highest,
                highest == 0 ? null : format(STUDENT, year, highest));
    }

    /**
     * Sets the next Student ID number the office wants to start issuing from (Automation Admin
     * -> Settings -> Student ID Series). Runs in its own transaction — this is a standalone admin
     * action, not part of creating a student. The row lock held for the update means a student
     * being created at the same moment either fully happens before or fully after this change.
     *
     * <p>Rejects any value that would let the very next generated ID collide with (or fall
     * behind) an ID already in use this year: the new next number must be strictly greater than
     * the highest existing Student ID number for the year. Existing Student IDs are never
     * touched — only future generation is affected.
     */
    @Transactional
    public StudentIdSeriesInfo setNextStudentNumber(int newNextNumber, User actor, String ip) {
        if (newNextNumber < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The next Student ID number must be 1 or greater.");
        }
        int year = LocalDate.now(INDIA).getYear();
        int highest = highestExistingStudentNumber(year);
        if (newNextNumber <= highest) {
            throw new ApiException(HttpStatus.CONFLICT, "Student ID " + format(STUDENT, year, newNextNumber)
                    + " is already in use (or below the highest existing ID, " + format(STUDENT, year, highest)
                    + "). Choose a number greater than " + highest + " so no existing Student ID can be reused.");
        }
        AcadSequence seq = sequences.findForUpdate(STUDENT, year)
                .orElseGet(() -> sequences.saveAndFlush(new AcadSequence(STUDENT, year)));
        int before = seq.getLastNumber();
        seq.setLastNumber(newNextNumber - 1);
        sequences.save(seq);
        auditService.record(actor, "ACAD_STUDENT_ID_SEQUENCE_CHANGED", "AcadSequence", seq.getId(),
                "Student ID series for " + year + ": next number changed from " + (before + 1) + " to " + newNextNumber
                        + " (" + format(STUDENT, year, newNextNumber) + ")", ip);
        return new StudentIdSeriesInfo(year, newNextNumber, format(STUDENT, year, newNextNumber), highest,
                highest == 0 ? null : format(STUDENT, year, highest));
    }

    private int highestExistingStudentNumber(int year) {
        return students.maxStudentIdForYear(String.valueOf(year))
                .map(id -> { Matcher m = STUDENT_ID.matcher(id.trim()); return m.matches() ? Integer.parseInt(m.group(2)) : 0; })
                .orElse(0);
    }

    private static String format(String kind, int year, int n) {
        return switch (kind) {
            case STUDENT -> String.format("LSA/%d/%04d", year, n);
            case RECEIPT -> String.format("LSR/%d/%04d", year, n);
            case PAYMENT -> String.format("PAY/%d/%05d", year, n);
            default -> throw new IllegalArgumentException("Unknown sequence kind " + kind);
        };
    }

    /**
     * Canonical form of a Student ID as it appears anywhere in the workbook:
     * "LSA2026/0001", "lsa/2026/1" and "LSA/2026/0001" all become "LSA/2026/0001".
     */
    public static String canonicalStudentId(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = STUDENT_ID.matcher(raw.trim().replace(" ", ""));
        if (!m.matches()) {
            return null;
        }
        return String.format("LSA/%s/%04d", m.group(1), Integer.parseInt(m.group(2)));
    }
}
