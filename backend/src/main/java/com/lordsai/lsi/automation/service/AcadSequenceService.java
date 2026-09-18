package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadSequence;
import com.lordsai.lsi.automation.repository.AcadSequenceRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
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

    public AcadSequenceService(AcadSequenceRepository sequences, AcadStudentRepository students) {
        this.sequences = sequences;
        this.students = students;
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
