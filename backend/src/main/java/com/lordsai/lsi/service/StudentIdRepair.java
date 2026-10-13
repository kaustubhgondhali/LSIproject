package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.AuditLog;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time data repair, safe to run on every start: before V32, "Change User ID" overwrote the
 * academy-issued Student ID (student_profiles.student_id) with the student's chosen ID. For every such
 * profile this moves the chosen ID to login_id (so the student keeps signing in with it) and gives the
 * Student ID back — the original one recorded in the audit log, or a newly generated one if the log
 * has no record. A profile that holds an academy Student ID but still has no User ID gets that Student
 * ID text as its User ID (the same backfill as V34), so every student can sign in with a User ID.
 */
@Component
public class StudentIdRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StudentIdRepair.class);
    private static final Pattern ACADEMY_ID = Pattern.compile("(?i)^LSI-\\d{4}-\\d{5}$");
    /** The description the old Change User ID wrote: "Student ID changed from X to Y by the student". */
    private static final Pattern OLD_CHANGE = Pattern.compile("^Student ID changed from (\\S+) to ");

    private final StudentProfileRepository profileRepository;
    private final AuditLogRepository auditLogRepository;
    private final StudentIdService studentIdService;
    private final AuditService auditService;

    public StudentIdRepair(StudentProfileRepository profileRepository, AuditLogRepository auditLogRepository,
                           StudentIdService studentIdService, AuditService auditService) {
        this.profileRepository = profileRepository;
        this.auditLogRepository = auditLogRepository;
        this.studentIdService = studentIdService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repair();
    }

    @Transactional
    public int repair() {
        int repaired = 0;
        for (StudentProfile p : profileRepository.findAll()) {
            if (p.getLoginId() != null || p.getStudentId() == null || p.isAcademyEbookOnly()) {
                continue;   // an academy student's ebook login keeps its academy Student ID (LSA/...)
            }
            if (ACADEMY_ID.matcher(p.getStudentId()).matches()) {
                p.setLoginId(p.getStudentId());
                profileRepository.saveAndFlush(p);
                continue;
            }
            String chosen = p.getStudentId();
            String original = originalStudentId(p);
            boolean restored = original != null && profileRepository.findByStudentIdIgnoreCase(original).isEmpty();
            String studentId = restored ? original : studentIdService.next();
            p.setLoginId(chosen);
            p.setStudentId(studentId);
            profileRepository.saveAndFlush(p);
            auditService.record(null, "STUDENT_ID_RESTORED", "StudentProfile", p.getId(),
                    "Student ID " + (restored ? "restored to " : "re-issued as ") + studentId
                            + "; the student's chosen ID " + chosen + " is now their User ID", null);
            log.info("[STUDENT ID] Profile {}: Student ID {} {}, User ID {}", p.getId(), restored ? "restored to" : "re-issued as",
                    studentId, chosen);
            repaired++;
        }
        return repaired;
    }

    /** The ID the profile had before its first student-made change, from the audit log. */
    private String originalStudentId(StudentProfile p) {
        List<AuditLog> entries = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("StudentProfile", p.getId(), Pageable.unpaged()).getContent();
        String earliest = null;
        for (AuditLog entry : entries) { // newest first: the last match is the first change
            if (!"USER_ID_CHANGED".equals(entry.getAction()) || entry.getDescription() == null) {
                continue;
            }
            Matcher m = OLD_CHANGE.matcher(entry.getDescription());
            if (m.find()) {
                earliest = m.group(1);
            }
        }
        return earliest != null && ACADEMY_ID.matcher(earliest).matches() ? earliest : null;
    }
}
