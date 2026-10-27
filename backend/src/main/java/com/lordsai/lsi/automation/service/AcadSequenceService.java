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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the academy's business numbers, unique under concurrent use:
 *   students  LSA/YYYY/0001 — LSA is fixed; YYYY is the current calendar year (Asia/Kolkata);
 *                             only the four-digit serial is changed by the office
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
    /** Owner-typed next Student ID: must be LSA/{current year}/{serial}. */
    private static final Pattern SERIES_ID = Pattern.compile("^LSA/(\\d{4})/(\\d{1,6})$", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_WIDTH = 4;
    /** Website (online) Student IDs are LSI-YYYY-NNNNN; academy Student IDs must never look like them. */
    static final String WEBSITE_PREFIX = "LSI";
    static final String WEBSITE_PREFIX_MESSAGE = "Student IDs starting with LSI belong to the website's online students. "
            + "Academy Student IDs are always LSA/YYYY/0001 (YYYY = the current calendar year).";
    /** acad_students.student_id is VARCHAR(30). */
    private static final int MAX_STUDENT_ID = 30;

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
            n = Math.max(n, highestExistingStudentNumber(defaultStudentPrefix(year)) + 1);
            clearCustomStudentFormat(seq);
        }
        seq.setLastNumber(n);
        sequences.save(seq);
        return STUDENT.equals(kind) ? formatStudent(year, n) : format(kind, year, n);
    }

    /** A preview for the form ("your next Student ID will be …"); nothing is reserved. */
    @Transactional(readOnly = true)
    public String peek(String kind) {
        int year = LocalDate.now(INDIA).getYear();
        AcadSequence seq = sequences.findByKindAndYear(kind, year).orElse(null);
        int n = (seq == null ? 0 : seq.getLastNumber()) + 1;
        if (STUDENT.equals(kind)) {
            n = Math.max(n, highestExistingStudentNumber(defaultStudentPrefix(year)) + 1);
            return formatStudent(year, n);
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
        AcadSequence seq = sequences.findByKindAndYear(STUDENT, year).orElse(null);
        int highest = highestExistingStudentNumber(defaultStudentPrefix(year));
        int configured = seq == null ? 0 : seq.getLastNumber();
        int next = Math.max(configured, highest) + 1;
        return new StudentIdSeriesInfo(year, next, formatStudent(year, next), highest,
                highest == 0 ? null : formatStudent(year, highest));
    }

    /**
     * Sets the next four-digit serial the office wants to start issuing from (Automation Admin
     * → Settings / Add Student → Change Student ID series). LSA and the current year are fixed;
     * only this number moves. Existing Student IDs are never touched.
     */
    @Transactional
    public StudentIdSeriesInfo setNextStudentNumber(int newNextNumber, User actor, String ip) {
        if (newNextNumber < 1 || newNextNumber > 999999) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The next Student ID number must be between 1 and 999999.");
        }
        int year = LocalDate.now(INDIA).getYear();
        AcadSequence seq = sequences.findForUpdate(STUDENT, year)
                .orElseGet(() -> sequences.saveAndFlush(new AcadSequence(STUDENT, year)));
        clearCustomStudentFormat(seq);
        int highest = highestExistingStudentNumber(defaultStudentPrefix(year));
        if (newNextNumber <= highest) {
            throw new ApiException(HttpStatus.CONFLICT, "Student ID " + formatStudent(year, newNextNumber)
                    + " is already in use (or below the highest existing ID, " + formatStudent(year, highest)
                    + "). Choose a number greater than " + highest + " so no existing Student ID can be reused.");
        }
        int before = seq.getLastNumber();
        seq.setLastNumber(newNextNumber - 1);
        sequences.save(seq);
        auditService.record(actor, "ACAD_STUDENT_ID_SEQUENCE_CHANGED", "AcadSequence", seq.getId(),
                "Student ID series for " + year + ": next number changed from " + (before + 1) + " to " + newNextNumber
                        + " (" + formatStudent(year, newNextNumber) + ")", ip);
        return new StudentIdSeriesInfo(year, newNextNumber, formatStudent(year, newNextNumber), highest,
                highest == 0 ? null : formatStudent(year, highest));
    }

    /**
     * Automation Admin > Add Student > "Change Student ID series": the owner types the next full
     * Student ID as LSA/{current year}/{serial}. LSA and the year cannot be chosen — they must match
     * the current calendar year. Only the serial is stored. Custom formats (e.g. LSA-B3-0001) and
     * website IDs (LSI...) are refused. Existing IDs are not touched.
     */
    @Transactional
    public StudentIdSeriesInfo setNextStudentId(String nextStudentId, User actor, String ip) {
        String typed = nextStudentId == null ? "" : nextStudentId.trim().toUpperCase(Locale.ROOT);
        if (typed.startsWith(WEBSITE_PREFIX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WEBSITE_PREFIX_MESSAGE);
        }
        Matcher m = SERIES_ID.matcher(typed);
        int year = LocalDate.now(INDIA).getYear();
        if (!m.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the next Student ID as LSA/" + year
                    + "/0001 — LSA is fixed, the year is the current year, and only the four-digit number can be chosen.");
        }
        int typedYear = Integer.parseInt(m.group(1));
        if (typedYear != year) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The year in the Student ID must be the current year ("
                    + year + "), not " + typedYear + ". LSA/" + year + "/" + m.group(2) + " is the correct form.");
        }
        int number = Integer.parseInt(m.group(2));
        if (number < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The number part of the Student ID must be 1 or greater.");
        }
        if (typed.length() > MAX_STUDENT_ID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A Student ID can be at most " + MAX_STUDENT_ID + " characters.");
        }
        return setNextStudentNumber(number, actor, ip);
    }

    /** Highest number already used by a Student ID made of this prefix followed only by digits. */
    private int highestExistingStudentNumber(String prefix) {
        int highest = 0;
        for (String id : students.findStudentIdsStartingWith(prefix)) {
            String t = id.trim();
            if (t.length() <= prefix.length() || !t.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String rest = t.substring(prefix.length());
            if (rest.length() <= 9 && rest.chars().allMatch(Character::isDigit)) {
                highest = Math.max(highest, Integer.parseInt(rest));
            }
        }
        return highest;
    }

    private static String defaultStudentPrefix(int year) {
        return "LSA/" + year + "/";
    }

    /** Always LSA/{current-or-given year}/{4 digits} — custom prefixes from older series edits are ignored. */
    private static String formatStudent(int year, int n) {
        return defaultStudentPrefix(year) + pad(n, DEFAULT_WIDTH);
    }

    private static void clearCustomStudentFormat(AcadSequence seq) {
        if (seq.getPrefix() != null || seq.getPadWidth() != null) {
            seq.setPrefix(null);
            seq.setPadWidth(null);
        }
    }

    private static String pad(int n, int width) {
        String s = Integer.toString(n);
        return s.length() >= width ? s : "0".repeat(width - s.length()) + s;
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
