package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.EbookAccess;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.dto.ebook.EbookDtos.AcademyEbook;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.service.AuditService;
import com.lordsai.lsi.service.AuthService;
import com.lordsai.lsi.service.DeviceBindingService;
import com.lordsai.lsi.service.EbookEntitlementService;
import com.lordsai.lsi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Automation Admin: the free ebook login of an academy (office) student. When the first
 * installment is recorded the student gets a Student Panel login — their own User ID and a
 * password — that opens only the ebook, free; it keeps the academy Student ID (LSA/...). The
 * login details are emailed, and the office can see the first password (to share it) until the
 * student changes it, reset the password or the student's device, and block / unblock the ebook.
 * A student whose email already has a website login gets the ebook added to that login instead.
 */
@Service
public class AcadEbookAccessService {

    public static final String LOGIN_PAGE = "/student-login.html";

    private final AcadStudentRepository students;
    private final AcadPaymentRepository payments;
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final UserService userService;
    private final EbookEntitlementService entitlements;
    private final DeviceBindingService deviceBinding;
    private final EmailService emailService;
    private final SecretCrypto crypto;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AppProperties properties;

    public AcadEbookAccessService(AcadStudentRepository students, AcadPaymentRepository payments, UserRepository users,
                                  StudentProfileRepository profiles, UserService userService, EbookEntitlementService entitlements,
                                  DeviceBindingService deviceBinding, EmailService emailService, SecretCrypto crypto,
                                  PasswordEncoder passwordEncoder, AuditService auditService, AppProperties properties) {
        this.students = students;
        this.payments = payments;
        this.users = users;
        this.profiles = profiles;
        this.userService = userService;
        this.entitlements = entitlements;
        this.deviceBinding = deviceBinding;
        this.emailService = emailService;
        this.crypto = crypto;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public EbookAccess status(Long acadStudentId) {
        return view(require(acadStudentId), null);
    }

    /**
     * After a payment is recorded: the first one makes the login (when the student has an email),
     * a later one gives any newly added ebook. Returns a line for the office, or null.
     */
    @Transactional
    public String afterPayment(Long acadStudentId, User actor, String ip) {
        AcadStudent s = require(acadStudentId);
        if (s.getPortalUser() != null) {
            entitlements.grantAcademyEbooks(s.getPortalUser(), s.isEbookBlocked(), actor, ip);
            return null;
        }
        if (blank(s.getEmail())) {
            return "No ebook login yet: add " + s.getFullName() + "'s email (Edit) to create it.";
        }
        return create(s, actor, ip).message();
    }

    /** The office's "Create ebook login" — for a student who paid before this existed, or had no email then. */
    @Transactional
    public EbookAccess create(Long acadStudentId, User actor, String ip) {
        AcadStudent s = require(acadStudentId);
        if (s.getPortalUser() != null) {
            throw new ApiException(HttpStatus.CONFLICT, s.getFullName() + " already has an ebook login.");
        }
        if (payments.countByStudentId(s.getId()) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The ebook login is made after the first installment is paid.");
        }
        if (blank(s.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add " + s.getFullName() + "'s email (Edit) to create the ebook login.");
        }
        return create(s, actor, ip);
    }

    private EbookAccess create(AcadStudent s, User actor, String ip) {
        String email = AuthService.normalizeEmail(s.getEmail());
        Optional<User> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() != Role.STUDENT) {
                throw new ApiException(HttpStatus.CONFLICT, "The email " + email + " belongs to a staff login. Give "
                        + s.getFullName() + " their own email address (Edit), then create the ebook login.");
            }
            students.findByPortalUserId(u.getId()).filter(other -> !other.getId().equals(s.getId())).ifPresent(other -> {
                throw new ApiException(HttpStatus.CONFLICT, "The email " + email + " is already used by the ebook login of "
                        + other.getStudentId() + " (" + other.getFullName() + "). Give " + s.getFullName() + " their own email address.");
            });
            // An online student of the website: the ebook is added to the login they already have.
            s.setPortalUser(u);
            s.setPortalPasswordEnc(null);
            s.setPortalCreatedAt(Instant.now());
            students.save(s);
            entitlements.grantAcademyEbooks(u, s.isEbookBlocked(), actor, ip);
            auditService.record(actor, "ACAD_EBOOK_LOGIN_LINKED", "AcadStudent", s.getId(),
                    s.getStudentId() + ": the ebook was added to the existing website login of " + email, ip);
            return view(s, "This email already has a website login, so the ebook was added to it. " + s.getFullName()
                    + " signs in with their existing User ID and password.");
        }

        UserService.StudentCreation login = userService.createAcademyEbookLogin(s.getFullName(), email, s.getMobile(),
                s.getStudentId(), s.getBatch() == null ? null : s.getBatch().getName());
        s.setPortalUser(login.user());
        s.setPortalPasswordEnc(crypto.encrypt(login.temporaryPassword()));
        s.setPortalCreatedAt(Instant.now());
        entitlements.grantAcademyEbooks(login.user(), s.isEbookBlocked(), actor, ip);
        String userId = login.profile().userId();
        EmailDelivery mail = emailService.sendAcademyEbookLogin(login.user(), userId, login.temporaryPassword(), loginUrl());
        if (mail.delivered()) {
            s.setPortalEmailedAt(Instant.now());
        }
        students.save(s);
        auditService.record(actor, "ACAD_EBOOK_LOGIN_CREATED", "AcadStudent", s.getId(), s.getStudentId()
                + ": Student Panel login (ebook only) created, User ID " + userId + (mail.delivered() ? ", emailed to " + email
                : "; the email could not be sent (" + mail.reason() + ")"), ip);
        return view(s, "Ebook login created: User ID " + userId + (mail.delivered() ? ", emailed to " + email + "."
                : ". The email could not be sent — share the login from the student's Ebook Login tab."));
    }

    /** A new password, shown to the office and emailed; the student's open sessions end. */
    @Transactional
    public EbookAccess resetPassword(Long acadStudentId, User actor, String ip) {
        AcadStudent s = requireLogin(acadStudentId);
        User u = s.getPortalUser();
        String password = userService.issueReplacementPassword(u);
        s.setPortalPasswordEnc(crypto.encrypt(password));
        String userId = profiles.findByUserId(u.getId()).map(StudentProfile::userId).orElse(u.getEmail());
        EmailDelivery mail = emailService.sendAcademyEbookLogin(u, userId, password, loginUrl());
        if (mail.delivered()) {
            s.setPortalEmailedAt(Instant.now());
        }
        students.save(s);
        auditService.record(actor, "ACAD_EBOOK_PASSWORD_RESET", "AcadStudent", s.getId(), s.getStudentId()
                + ": new ebook login password" + (mail.delivered() ? ", emailed to " + u.getEmail() : "; the email could not be sent"), ip);
        return view(s, "New password made" + (mail.delivered() ? " and emailed to " + u.getEmail() + "." : ". The email could not be sent — share it from here."));
    }

    /** Clears the registered phone / computer: the student can sign in on a new one. */
    @Transactional
    public EbookAccess resetDevice(Long acadStudentId, User actor, String ip) {
        AcadStudent s = requireLogin(acadStudentId);
        deviceBinding.adminResetDevice(s.getPortalUser().getId(), actor, ip);
        auditService.record(actor, "ACAD_EBOOK_DEVICE_RESET", "AcadStudent", s.getId(), s.getStudentId() + ": ebook login device reset", ip);
        return view(s, "Device reset: " + s.getFullName() + " can now sign in on a new phone or computer.");
    }

    /** Emails the current login details again (only while the office still knows the password). */
    @Transactional
    public EbookAccess emailLogin(Long acadStudentId, User actor, String ip) {
        AcadStudent s = requireLogin(acadStudentId);
        User u = s.getPortalUser();
        String password = visiblePassword(s, u);
        if (password == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, s.getFullName()
                    + " has set their own password, so it cannot be emailed. Use Reset password to send new login details.");
        }
        String userId = profiles.findByUserId(u.getId()).map(StudentProfile::userId).orElse(u.getEmail());
        EmailDelivery mail = emailService.sendAcademyEbookLogin(u, userId, password, loginUrl());
        if (!mail.delivered()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The login details could not be emailed. " + mail.reason());
        }
        s.setPortalEmailedAt(Instant.now());
        students.save(s);
        auditService.record(actor, "ACAD_EBOOK_LOGIN_EMAILED", "AcadStudent", s.getId(), s.getStudentId() + ": login details emailed to " + u.getEmail(), ip);
        return view(s, "Login details emailed to " + u.getEmail() + ".");
    }

    /** Blocks / unblocks the ebook the academy gave this student (also before the login exists). */
    @Transactional
    public EbookAccess setBlocked(Long acadStudentId, boolean blocked, User actor, String ip) {
        AcadStudent s = require(acadStudentId);
        s.setEbookBlocked(blocked);
        students.save(s);
        if (s.getPortalUser() != null) {
            entitlements.setAcademyEbooksBlocked(s.getPortalUser(), blocked, actor, ip);
        }
        auditService.record(actor, blocked ? "ACAD_EBOOK_BLOCKED" : "ACAD_EBOOK_UNBLOCKED", "AcadStudent", s.getId(),
                s.getStudentId() + ": ebook access " + (blocked ? "blocked" : "unblocked"), ip);
        return view(s, blocked ? "Ebook access blocked. " + s.getFullName() + " sees \"Your ebook access is blocked. Please contact the academy.\""
                : "Ebook access unblocked.");
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private EbookAccess view(AcadStudent s, String message) {
        User u = s.getPortalUser();
        if (u == null) {
            String state = payments.countByStudentId(s.getId()) == 0 ? "NO_PAYMENT" : blank(s.getEmail()) ? "NEEDS_EMAIL" : "READY";
            String text = message != null ? message : switch (state) {
                case "NO_PAYMENT" -> "The ebook login is made automatically when the first installment is paid.";
                case "NEEDS_EMAIL" -> "Add " + s.getFullName() + "'s email (Edit) to create the ebook login.";
                default -> s.getFullName() + " has paid, so the ebook login can be created now.";
            };
            return new EbookAccess(state, text, s.getStudentId(), s.getFullName(), s.getMobile(), s.getEmail(), null, null, false, false,
                    null, null, null, null, List.of(), loginUrl(), s.isEbookBlocked());
        }
        StudentProfile profile = profiles.findByUserId(u.getId()).orElse(null);
        boolean linked = profile == null || !profile.isAcademyEbookOnly();
        String password = visiblePassword(s, u);
        boolean changed = !linked && password == null;
        String device = deviceBinding.getStudentDevice(u.getId()).map(d -> d.deviceName()).orElse(null);
        List<AcademyEbook> ebooks = entitlements.academyEbooks(u.getId());
        return new EbookAccess(s.isEbookBlocked() ? "BLOCKED" : "ACTIVE", message, s.getStudentId(), s.getFullName(), s.getMobile(),
                u.getEmail(), profile == null ? null : profile.userId(), password, changed, linked, s.getPortalCreatedAt(), s.getPortalEmailedAt(),
                u.getLastLoginAt(), device, ebooks, loginUrl(), s.isEbookBlocked());
    }

    /** The first / office-reset password while it is still the student's password; null once they changed it. */
    private String visiblePassword(AcadStudent s, User u) {
        if (s.getPortalPasswordEnc() == null || u.getPasswordHash() == null) {
            return null;
        }
        try {
            String plain = crypto.decrypt(s.getPortalPasswordEnc());
            return passwordEncoder.matches(plain, u.getPasswordHash()) ? plain : null;
        } catch (RuntimeException e) {
            return null;   // stored under another server secret: treat as unknown
        }
    }

    private String loginUrl() {
        String base = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl().replaceAll("/+$", "");
        return base + LOGIN_PAGE;
    }

    private AcadStudent require(Long id) {
        return students.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    private AcadStudent requireLogin(Long id) {
        AcadStudent s = require(id);
        if (s.getPortalUser() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, s.getFullName() + " has no ebook login yet.");
        }
        return s;
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
