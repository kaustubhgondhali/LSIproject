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
    /** An office-chosen next Student ID: an optional prefix of [A-Z0-9/.-] ending in its number. */
    private static final Pattern SERIES_ID = Pattern.compile("^([A-Z0-9/.\\-]*?)(\\d{1,9})$");
    private static final int DEFAULT_WIDTH = 4;
    /** Website (online) Student IDs are LSI-YYYY-NNNNN; academy Student IDs must never look like them. */
    static final String WEBSITE_PREFIX = "LSI";
    static final String WEBSITE_PREFIX_MESSAGE = "Student IDs starting with LSI belong to the website's online students. "
            + "Use an academy format such as LSA/2026/0100 or LSA-B3-0001, so the two never clash.";
    private static final int MAX_PREFIX = 24;
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
            n = Math.max(n, highestExistingStudentNumber(studentPrefix(seq, year)) + 1);
        }
        seq.setLastNumber(n);
        sequences.save(seq);
        return STUDENT.equals(kind) ? formatStudent(seq, year, n) : format(kind, year, n);
    }

    /** A preview for the form ("your next Student ID will be …"); nothing is reserved. */
    @Transactional(readOnly = true)
    public String peek(String kind) {
        int year = LocalDate.now(INDIA).getYear();
        AcadSequence seq = sequences.findByKindAndYear(kind, year).orElse(null);
        int n = (seq == null ? 0 : seq.getLastNumber()) + 1;
        if (STUDENT.equals(kind)) {
            n = Math.max(n, highestExistingStudentNumber(studentPrefix(seq, year)) + 1);
            return formatStudent(seq, year, n);
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
        int highest = highestExistingStudentNumber(studentPrefix(seq, year));
        int configured = seq == null ? 0 : seq.getLastNumber();
        int next = Math.max(configured, highest) + 1;
        return new StudentIdSeriesInfo(year, next, formatStudent(seq, year, next), highest,
                highest == 0 ? null : formatStudent(seq, year, highest));
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
        AcadSequence seq = sequences.findForUpdate(STUDENT, year)
                .orElseGet(() -> sequences.saveAndFlush(new AcadSequence(STUDENT, year)));
        // Keeps the series' current prefix and width; only the number moves.
        int highest = highestExistingStudentNumber(studentPrefix(seq, year));
        if (newNextNumber <= highest) {
            throw new ApiException(HttpStatus.CONFLICT, "Student ID " + formatStudent(seq, year, newNextNumber)
                    + " is already in use (or below the highest existing ID, " + formatStudent(seq, year, highest)
                    + "). Choose a number greater than " + highest + " so no existing Student ID can be reused.");
        }
        int before = seq.getLastNumber();
        seq.setLastNumber(newNextNumber - 1);
        sequences.save(seq);
        auditService.record(actor, "ACAD_STUDENT_ID_SEQUENCE_CHANGED", "AcadSequence", seq.getId(),
                "Student ID series for " + year + ": next number changed from " + (before + 1) + " to " + newNextNumber
                        + " (" + formatStudent(seq, year, newNextNumber) + ")", ip);
        return new StudentIdSeriesInfo(year, newNextNumber, formatStudent(seq, year, newNextNumber), highest,
                highest == 0 ? null : formatStudent(seq, year, highest));
    }

    /**
     * Automation Admin > Add Student > "Change series": the office types the Student ID the NEXT
     * student should get (e.g. "LSA/2026/0100" or a new format such as "LSA-B3-0001"). Its
     * prefix and digit width become this year's series and later students count up from it
     * ("LSA-B3-0002", …). An ID starting with "LSI" is refused: that is the website's (online
     * students') format, and the two series must never clash. Refused when that ID, or a higher one in the same series, already
     * exists, so an issued Student ID can never be generated again. Existing IDs are not touched.
     */
    @Transactional
    public StudentIdSeriesInfo setNextStudentId(String nextStudentId, User actor, String ip) {
        String typed = nextStudentId == null ? "" : nextStudentId.trim().toUpperCase(Locale.ROOT);
        Matcher m = SERIES_ID.matcher(typed);
        if (!m.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the next Student ID, ending in its number — for example LSA/2026/0100 or LSA-B3-0001."
                    + " Use only letters, digits, '/', '-' and '.'.");
        }
        if (typed.startsWith(WEBSITE_PREFIX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WEBSITE_PREFIX_MESSAGE);
        }
        String prefix = m.group(1);
        String digits = m.group(2);
        if (prefix.length() > MAX_PREFIX || typed.length() > MAX_STUDENT_ID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A Student ID can be at most " + MAX_STUDENT_ID
                    + " characters, with a prefix of at most " + MAX_PREFIX + ".");
        }
        int number = Integer.parseInt(digits);
        if (number < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The number part of the Student ID must be 1 or greater.");
        }
        int year = LocalDate.now(INDIA).getYear();
        AcadSequence seq = sequences.findForUpdate(STUDENT, year)
                .orElseGet(() -> sequences.saveAndFlush(new AcadSequence(STUDENT, year)));
        int highest = highestExistingStudentNumber(prefix);
        if (number <= highest) {
            throw new ApiException(HttpStatus.CONFLICT, "Student ID " + typed + " is already in use (or below the highest existing ID in this series, "
                    + prefix + pad(highest, digits.length()) + "). Choose a number greater than " + highest + " so no Student ID is issued twice.");
        }
        String before = formatStudent(seq, year, Math.max(seq.getLastNumber(), highestExistingStudentNumber(studentPrefix(seq, year))) + 1);
        boolean isDefault = prefix.equals(defaultStudentPrefix(year)) && digits.length() == DEFAULT_WIDTH;
        seq.setPrefix(isDefault ? null : prefix);
        seq.setPadWidth(isDefault ? null : digits.length());
        seq.setLastNumber(number - 1);
        sequences.save(seq);
        auditService.record(actor, "ACAD_STUDENT_ID_SEQUENCE_CHANGED", "AcadSequence", seq.getId(),
                "Student ID series for " + year + ": next ID changed from " + before + " to " + typed, ip);
        return new StudentIdSeriesInfo(year, number, typed, highest, highest == 0 ? null : prefix + pad(highest, digits.length()));
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

    private static String studentPrefix(AcadSequence seq, int year) {
        // A website-style prefix (LSI...) is never used for an academy student, even if one was saved before this rule.
        return seq != null && seq.getPrefix() != null && !seq.getPrefix().toUpperCase(Locale.ROOT).startsWith(WEBSITE_PREFIX)
                ? seq.getPrefix() : defaultStudentPrefix(year);
    }

    private static String formatStudent(AcadSequence seq, int year, int n) {
        int width = seq != null && seq.getPadWidth() != null ? seq.getPadWidth() : DEFAULT_WIDTH;
        return studentPrefix(seq, year) + pad(n, width);
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
