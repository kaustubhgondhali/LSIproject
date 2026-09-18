package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.auth.AuthDtos.LoginResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.TokenCheckResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.UserSummary;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.AccountToken;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.SessionRevokeReason;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.security.AuthUser;
import com.lordsai.lsi.security.JwtService;
import com.lordsai.lsi.security.LoginAttemptService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email/student ID or password.";
    private static final String ACCOUNT_DISABLED = "This account has been disabled. Please contact the academy.";
    private static final String ACCOUNT_PENDING = "Your account is not set up yet. Please use the setup link from your email, or request a password reset.";
    private static final String LOCKED = "Too many failed attempts. Please try again after 15 minutes.";
    private static final String STUDENT_PORTAL_ONLY = "Only students can log in through Student Admin.";
    private static final String ADMIN_PORTAL_ONLY = "Only administrators can log in through Admin Login.";
    private static final String AUTOMATION_PORTAL_ONLY = "Only the Automation Admin can log in through the Automation Admin portal.";
    private static final String ROLE_RETIRED = "Teacher accounts are no longer supported. Please contact the academy.";
    private static final String USER_ID_TAKEN = "That User ID is already in use. Please choose a different one.";
    /** The shape StudentIdService issues; kept for academy-generated IDs so a chosen ID can never collide with a future one. */
    private static final Pattern ACADEMY_ISSUED_ID = Pattern.compile("(?i)^LSI-\\d{4}-\\d{5}$");

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AccountTokenService accountTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final EnrollmentService enrollmentService;
    private final EbookEntitlementService ebookEntitlementService;

    public AuthService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SessionService sessionService,
                       AccountTokenService accountTokenService,
                       LoginAttemptService loginAttemptService,
                       AuditService auditService,
                       EmailService emailService,
                       EnrollmentService enrollmentService,
                       EbookEntitlementService ebookEntitlementService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.accountTokenService = accountTokenService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.enrollmentService = enrollmentService;
        this.ebookEntitlementService = ebookEntitlementService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String identifier, String rawPassword, String deviceInfo, String ipAddress) {
        return login(identifier, rawPassword, null, deviceInfo, ipAddress);
    }

    /**
     * @param portal "STUDENT" or "ADMIN" — the login screen used. A correct password is not enough:
     *               the account's role must match the portal, otherwise the login is refused and
     *               no session is created. Enforced here, on the server, not in the browser.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String identifier, String rawPassword, String portal, String deviceInfo, String ipAddress) {
        String cleanId = identifier == null ? "" : identifier.trim();

        if (loginAttemptService.isLocked(cleanId)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, LOCKED);
        }

        Optional<User> found = findByIdentifier(cleanId);

        // An account that has never had a password set cannot be authenticated; point the
        // user at the setup link instead of a misleading "wrong password".
        if (found.isPresent() && found.get().getAccountStatus() == AccountStatus.PENDING_SETUP) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_PENDING);
        }

        // Run BCrypt even when the user is unknown so timing does not reveal valid identifiers.
        String hashToCheck = found.map(User::getPasswordHash)
                .filter(h -> h != null && !h.isBlank())
                .orElse("$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv");
        boolean passwordOk = passwordEncoder.matches(rawPassword, hashToCheck);

        if (found.isEmpty() || !passwordOk) {
            loginAttemptService.recordFailure(cleanId);
            auditService.record(found.orElse(null), "LOGIN_FAILED", "Failed login for '" + cleanId + "'", ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (!user.getRole().isUsable()) {
            auditService.record(user, "LOGIN_BLOCKED_ROLE", "Login attempt with retired role " + user.getRole(), ipAddress);
            throw new ApiException(HttpStatus.FORBIDDEN, ROLE_RETIRED);
        }
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            auditService.record(user, "LOGIN_BLOCKED_DISABLED", "Login attempt on disabled account", ipAddress);
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_DISABLED);
        }

        // Portal / role separation: Student Admin accepts STUDENT only, Admin Login accepts ADMIN only.
        Role required = portalRole(portal);
        if (required != null && user.getRole() != required) {
            auditService.record(user, "LOGIN_BLOCKED_PORTAL", "User", user.getId(),
                    user.getRole() + " account tried the " + required + " login portal", ipAddress);
            throw new ApiException(HttpStatus.FORBIDDEN,
                    required == Role.STUDENT ? STUDENT_PORTAL_ONLY
                            : required == Role.AUTOMATION_ADMIN ? AUTOMATION_PORTAL_ONLY : ADMIN_PORTAL_ONLY);
        }

        // Throws 409 for a student who already has a live session elsewhere.
        UserSession session = sessionService.open(user, jwtService.accessTokenTtl(), deviceInfo, ipAddress);

        loginAttemptService.recordSuccess(cleanId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.record(user, "LOGIN_SUCCESS", "User", user.getId(), "Logged in", ipAddress);

        String token = jwtService.issueAccessToken(user, session.getTokenId(), session.getExpiresAt());
        return new LoginResponse(token, "Bearer", session.getExpiresAt(), summarize(user), redirectFor(user.getRole()));
    }

    @Transactional
    public void logout(AuthUser current, String ipAddress) {
        sessionService.revoke(current.sessionId(), SessionRevokeReason.USER_LOGOUT);
        userRepository.findById(current.id()).ifPresent(u ->
                auditService.record(u, "LOGOUT", "User", u.getId(), "Logged out", ipAddress));
    }

    @Transactional
    public LoginResponse refresh(AuthUser current) {
        User user = userRepository.findById(current.id())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));

        UserSession session = sessionService.findActiveById(current.sessionId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));

        UserSession rotated = sessionService.rotate(session, jwtService.accessTokenTtl());
        String token = jwtService.issueAccessToken(user, rotated.getTokenId(), rotated.getExpiresAt());
        return new LoginResponse(token, "Bearer", rotated.getExpiresAt(), summarize(user), redirectFor(user.getRole()));
    }

    @Transactional(readOnly = true)
    public UserSummary me(AuthUser current) {
        return userRepository.findById(current.id()).map(this::summarize)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));
    }

    /**
     * Always responds successfully so the endpoint cannot be used to discover which emails
     * have accounts. The email is only actually sent when the account exists.
     */
    @Transactional
    public void forgotPassword(String email, String ipAddress) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(u -> u.getAccountStatus() != AccountStatus.DISABLED)
                .ifPresent(user -> {
                    String link = accountTokenService.issueLink(user, TokenPurpose.PASSWORD_RESET);
                    emailService.sendPasswordResetEmail(user, link);
                    auditService.record(user, "PASSWORD_RESET_REQUESTED", "User", user.getId(),
                            "Password reset link issued", ipAddress);
                });
    }

    /**
     * "I never received my enrollment email." Re-sends the Student ID and a fresh setup link.
     * Like {@link #forgotPassword}, it answers the same way whether or not the account exists, so
     * it cannot be used to discover which emails have accounts.
     */
    @Transactional
    public String resendSetupEmail(String email, String ipAddress) {
        final String[] message = {"If that email has an enrollment, we have re-sent the Student ID and password setup link."};
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(u -> u.getRole() == Role.STUDENT)
                .filter(u -> u.getAccountStatus() != AccountStatus.DISABLED)
                .ifPresent(user -> {
                    if (user.getAccountStatus() != AccountStatus.DISABLED) {
                        // Ebook buyers receive a fresh temporary password because the old one is never recoverable.
                        try {
                            com.lordsai.lsi.email.EmailDelivery delivery = ebookEntitlementService.resendLatestPurchaseEmail(user);
                            message[0] = delivery.delivered()
                                    ? "Your ebook account credentials, purchase details and invoice have been emailed."
                                    : "We could not send the ebook account email. Please try again or contact the academy.";
                            auditService.record(user, delivery.delivered() ? "EBOOK_CREDENTIALS_RESENT" : "EBOOK_CREDENTIALS_RESEND_FAILED",
                                    "User", user.getId(), "Ebook credential resend requested", ipAddress);
                            return;
                        } catch (ApiException ignored) {
                            // No completed ebook purchase: preserve the existing course setup flow.
                        }
                    }
                    enrollmentService.resendSetupEmail(user);
                    auditService.record(user, "SETUP_EMAIL_RESENT", "User", user.getId(),
                            "Setup email resent at student request", ipAddress);
                });
        return message[0];
    }

    @Transactional(readOnly = true)
    public TokenCheckResponse checkToken(String rawToken) {
        return accountTokenService.findUsable(rawToken)
                .map(t -> new TokenCheckResponse(true, maskEmail(t.getUser().getEmail()), t.getPurpose().name()))
                .orElse(new TokenCheckResponse(false, null, null));
    }

    /** Handles both first-time setup and reset; the token's purpose decides the audit label. */
    @Transactional
    public void setPasswordWithToken(String rawToken, String newPassword, String ipAddress) {
        AccountToken token = accountTokenService.findUsable(rawToken)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "This link is invalid or has expired. Please request a new one."));

        User user = token.getUser();
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_DISABLED);
        }

        applyNewPassword(user, newPassword);
        if (user.getAccountStatus() == AccountStatus.PENDING_SETUP) {
            user.setAccountStatus(AccountStatus.ACTIVE);
        }
        userRepository.save(user);
        accountTokenService.markUsed(token);

        String action = token.getPurpose() == TokenPurpose.ACCOUNT_SETUP ? "PASSWORD_SET_FIRST_TIME" : "PASSWORD_RESET";
        auditService.record(user, action, "User", user.getId(), "Password set via emailed link", ipAddress);
        // Best-effort security notice; a mail outage must never undo a completed reset.
        emailService.sendPasswordChanged(user);
    }

    @Transactional
    public void changePassword(AuthUser current, String currentPassword, String newPassword, String ipAddress) {
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        applyNewPassword(user, newPassword);
        userRepository.save(user);
        auditService.record(user, "PASSWORD_CHANGED", "User", user.getId(), "Password changed by user", ipAddress);
        emailService.sendPasswordChanged(user);
    }

    @Transactional
    public void changeAdminUserId(AuthUser current, String currentUserId, String newUserId,
                                  String currentPassword, String ipAddress) {
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.AUTOMATION_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can change this User ID.");
        }
        String currentId = user.getEmail().substring(0, user.getEmail().indexOf('@'));
        String requested = newUserId == null ? "" : newUserId.trim();
        if (!currentId.equalsIgnoreCase(currentUserId == null ? "" : currentUserId.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The current User ID does not match your account.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (requested.equalsIgnoreCase(currentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new User ID is the same as your current one.");
        }
        if (!userRepository.findByUsername(requested.toLowerCase()).stream()
                .allMatch(candidate -> candidate.getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "User ID is already in use.");
        }
        String domain = user.getEmail().substring(user.getEmail().indexOf('@'));
        user.setEmail(requested.toLowerCase() + domain);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "User ID is already in use.");
        }
        auditService.record(user, "ADMIN_USER_ID_CHANGED", "User", user.getId(),
                "User ID changed from " + currentId + " to " + requested, ipAddress);
    }

    /**
     * Lets a student change the User ID (Student ID) they sign in with. Only the
     * student_profiles.student_id value changes: the users row, enrollments, payments,
     * invoices and progress all key on the numeric user id, and so does the JWT subject,
     * so the current session stays valid. Returns the stored new ID.
     */
    @Transactional
    public String changeStudentUserId(AuthUser current, String currentUserId, String newUserId, String confirmUserId,
                                      String currentPassword, String ipAddress) {
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session is no longer valid."));
        if (user.getRole() != Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only student accounts have a User ID.");
        }
        StudentProfile profile = studentProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));

        String oldId = profile.getStudentId();
        String requested = newUserId == null ? "" : newUserId.trim();
        if (!oldId.equalsIgnoreCase(currentUserId == null ? "" : currentUserId.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The current User ID does not match your account.");
        }
        if (!requested.equals(confirmUserId == null ? "" : confirmUserId.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new User IDs do not match.");
        }
        if (requested.equalsIgnoreCase(oldId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new User ID is the same as your current one.");
        }
        if (ACADEMY_ISSUED_ID.matcher(requested).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDs in the format LSI-YYYY-NNNNN are issued by the academy and cannot be chosen.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        // Both a Student ID and a short username (email local part) are accepted at login,
        // so the new ID must be unique across both, ignoring the student's own account.
        if (studentProfileRepository.findByStudentIdIgnoreCase(requested).isPresent()
                || userRepository.findByUsername(requested.toLowerCase()).stream().anyMatch(u -> !u.getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, USER_ID_TAKEN);
        }

        profile.setStudentId(requested);
        try {
            studentProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            // Two students chose the same ID at the same moment; the unique constraint is the final word.
            throw new ApiException(HttpStatus.CONFLICT, USER_ID_TAKEN);
        }
        auditService.record(user, "USER_ID_CHANGED", "StudentProfile", profile.getId(),
                "Student ID changed from " + oldId + " to " + requested + " by the student", ipAddress);
        return requested;
    }

    // ----------------------------------------------------------------------------------------

    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        sessionService.revokeAll(user.getId(), SessionRevokeReason.PASSWORD_CHANGED);
    }

    private static Role portalRole(String portal) {
        if (portal == null || portal.isBlank()) {
            return null;
        }
        String p = portal.trim().toUpperCase();
        if (p.equals("STUDENT")) {
            return Role.STUDENT;
        }
        if (p.equals("ADMIN")) {
            return Role.ADMIN;
        }
        if (p.equals("AUTOMATION")) {
            return Role.AUTOMATION_ADMIN;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown login portal.");
    }

    private Optional<User> findByIdentifier(String identifier) {
        if (identifier.isBlank()) {
            return Optional.empty();
        }
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(normalizeEmail(identifier));
        }
        Optional<User> student = studentProfileRepository.findByStudentIdIgnoreCase(identifier).map(StudentProfile::getUser);
        if (student.isPresent()) {
            return student;
        }
        // Anyone may sign in with a short username: the part of their email before "@",
        // as long as it identifies exactly one account.
        List<User> matches = userRepository.findByUsername(identifier.toLowerCase());
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private UserSummary summarize(User user) {
        String studentId = user.getRole() == Role.STUDENT
                ? studentProfileRepository.findByUserId(user.getId()).map(StudentProfile::getStudentId).orElse(null)
                : null;
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getMobile(),
                user.getRole(), studentId, user.getLastLoginAt());
    }

    public static String redirectFor(Role role) {
        return switch (role) {
            case ADMIN -> "admin-dashboard.html";
            case AUTOMATION_ADMIN -> "automation-admin.html";
            default -> "student-dashboard.html";
        };
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
