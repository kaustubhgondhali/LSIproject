package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.auth.AuthDtos.LoginResponse;
import com.lordsai.lsi.dto.auth.AuthDtos.UserSummary;
import com.lordsai.lsi.dto.auth.DeviceBindingDtos.StudentDeviceResponse;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.DeviceVerificationOtp;
import com.lordsai.lsi.entity.StudentDevice;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.DeviceStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.SessionRevokeReason;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.DeviceVerificationOtpRepository;
import com.lordsai.lsi.repository.StudentDeviceRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.security.JwtService;
import com.lordsai.lsi.util.TokenUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Enforces Account-to-Device binding for student accounts:
 * ONE STUDENT ACCOUNT = ONE REGISTERED COMPUTER/DEVICE.
 *
 * Supports multi-browser authorization on the same physical computer,
 * protects against stolen tokens/replay attacks, provides controlled self-service
 * recovery with Email OTP, and exposes admin reset capabilities.
 */
@Service
public class DeviceBindingService {

    private static final Logger log = LoggerFactory.getLogger(DeviceBindingService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration TEMP_TOKEN_TTL = Duration.ofMinutes(15);

    private final StudentDeviceRepository studentDeviceRepository;
    private final DeviceVerificationOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final java.util.Map<Long, String> latestOtpCache = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${lsi.security.device-binding-enforced:false}")
    private boolean deviceBindingEnforced = false;

    public boolean isDeviceBindingEnforced() {
        return deviceBindingEnforced;
    }

    public void setDeviceBindingEnforced(boolean deviceBindingEnforced) {
        this.deviceBindingEnforced = deviceBindingEnforced;
    }

    public String getLatestOtpForTesting(Long userId) {
        return latestOtpCache.get(userId);
    }

    public DeviceBindingService(StudentDeviceRepository studentDeviceRepository,
                                DeviceVerificationOtpRepository otpRepository,
                                UserRepository userRepository,
                                StudentProfileRepository studentProfileRepository,
                                SessionService sessionService,
                                JwtService jwtService,
                                AuditService auditService,
                                EmailService emailService) {
        this.studentDeviceRepository = studentDeviceRepository;
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    public record DeviceCheckResult(
            boolean allowed,
            String deviceStatus, // "AUTHENTICATED", "DEVICE_REGISTRATION_REQUIRED", "DEVICE_LINK_REQUIRED", "DEVICE_BLOCKED"
            StudentDevice device,
            String tempToken,
            String emailMasked,
            String registeredDeviceName,
            String message
    ) {
        public static DeviceCheckResult allow(StudentDevice device) {
            return new DeviceCheckResult(true, "AUTHENTICATED", device, null, null, null, null);
        }

        public static DeviceCheckResult registrationRequired(String tempToken, String emailMasked) {
            return new DeviceCheckResult(false, "DEVICE_REGISTRATION_REQUIRED", null, tempToken, emailMasked, null,
                    "This account requires device registration. A verification code has been sent to your email.");
        }

        public static DeviceCheckResult linkRequired(String tempToken, String emailMasked, String registeredDeviceName) {
            return new DeviceCheckResult(false, "DEVICE_LINK_REQUIRED", null, tempToken, emailMasked, registeredDeviceName,
                    "This account is registered to " + registeredDeviceName + ". If you are using this same computer, enter the verification code sent to your email to link this browser.");
        }

        public static DeviceCheckResult blocked(String registeredDeviceName) {
            return new DeviceCheckResult(false, "DEVICE_BLOCKED", null, null, null, registeredDeviceName,
                    "This account is already registered to another device.");
        }
    }

    /**
     * Evaluates whether a login attempt is coming from the student's authorized device.
     */
    @Transactional
    public DeviceCheckResult evaluateLogin(User user, String providedDeviceId, String providedDeviceToken,
                                          String deviceInfo, String ipAddress) {
        if (user.getRole() != Role.STUDENT) {
            return DeviceCheckResult.allow(null);
        }

        if (!deviceBindingEnforced) {
            Optional<StudentDevice> registered = studentDeviceRepository.findByUserId(user.getId());
            StudentDevice device;
            if (registered.isEmpty()) {
                String devId = (providedDeviceId != null && !providedDeviceId.isBlank())
                        ? providedDeviceId
                        : "dev_" + UUID.randomUUID().toString().replace("-", "");
                String devSecret = (providedDeviceToken != null && !providedDeviceToken.isBlank())
                        ? providedDeviceToken
                        : TokenUtil.generateOpaqueToken();
                device = new StudentDevice();
                device.setUser(user);
                device.setDeviceId(devId);
                device.setDeviceName(cleanDeviceName(null, deviceInfo));
                device.setDevicePlatform(cleanDevicePlatform(deviceInfo));
                device.setDeviceSecretHash(TokenUtil.sha256Hex(devSecret));
                device.setDeviceStatus(DeviceStatus.ACTIVE);
                device.setResetRequired(false);
                Instant now = Instant.now();
                device.setRegisteredAt(now);
                device.setLastSeenAt(now);
                device.setLastVerifiedAt(now);
                studentDeviceRepository.save(device);
            } else {
                device = registered.get();
                device.setLastSeenAt(Instant.now());
                studentDeviceRepository.save(device);
            }
            return DeviceCheckResult.allow(device);
        }

        Optional<StudentDevice> registered = studentDeviceRepository.findByUserId(user.getId());
        if (registered.isEmpty()) {
            String tempToken = jwtService.issueTempDeviceToken(user.getId(), "DEVICE_REGISTRATION", TEMP_TOKEN_TTL);
            issueAndSendOtp(user, "REGISTRATION", null, deviceInfo, ipAddress, "device registration");
            return DeviceCheckResult.registrationRequired(tempToken, maskEmail(user.getEmail()));
        }

        StudentDevice device = registered.get();
        if (device.isResetRequired() || device.getDeviceStatus() == DeviceStatus.RESET_REQUIRED) {
            studentDeviceRepository.delete(device);
            String tempToken = jwtService.issueTempDeviceToken(user.getId(), "DEVICE_REGISTRATION", TEMP_TOKEN_TTL);
            issueAndSendOtp(user, "REGISTRATION", null, deviceInfo, ipAddress, "device registration after reset");
            return DeviceCheckResult.registrationRequired(tempToken, maskEmail(user.getEmail()));
        }

        if (providedDeviceId != null && providedDeviceToken != null) {
            if (device.getDeviceId().equals(providedDeviceId)
                    && device.getDeviceSecretHash().equals(TokenUtil.sha256Hex(providedDeviceToken))
                    && device.isActive()) {
                device.setLastSeenAt(Instant.now());
                studentDeviceRepository.save(device);
                return DeviceCheckResult.allow(device);
            }
        }

        // Credentials missing or mismatch: initiate browser link on the registered computer
        String tempToken = jwtService.issueTempDeviceToken(user.getId(), "LINK_BROWSER", TEMP_TOKEN_TTL);
        issueAndSendOtp(user, "LINK_BROWSER", device.getDeviceId(), deviceInfo, ipAddress,
                "authorizing this browser on registered device: " + device.getDeviceName());
        auditService.record(user, "DEVICE_LOGIN_BLOCKED", "User", user.getId(),
                "Device verification required for " + user.getEmail() + " (registered device: " + device.getDeviceName() + ")", ipAddress);
        return DeviceCheckResult.linkRequired(tempToken, maskEmail(user.getEmail()), device.getDeviceName());
    }

    /**
     * Completes first-time device registration after verifying the email OTP.
     */
    @Transactional
    public LoginResponse completeRegistration(String tempToken, String otp, String publicKey,
                                              String deviceName, String devicePlatform, String ipAddress) {
        User user = parseTempToken(tempToken, "DEVICE_REGISTRATION");
        verifyAndConsumeOtp(user, "REGISTRATION", otp, ipAddress);

        if (studentDeviceRepository.existsByUserId(user.getId())) {
            studentDeviceRepository.deleteByUserId(user.getId());
        }

        String deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "");
        String deviceSecret = TokenUtil.generateOpaqueToken();
        Instant now = Instant.now();

        StudentDevice dev = new StudentDevice();
        dev.setUser(user);
        dev.setDeviceId(deviceId);
        dev.setDeviceName(cleanDeviceName(deviceName, devicePlatform));
        dev.setDevicePlatform(cleanDevicePlatform(devicePlatform));
        dev.setDevicePublicKey(publicKey);
        dev.setDeviceSecretHash(TokenUtil.sha256Hex(deviceSecret));
        dev.setDeviceStatus(DeviceStatus.ACTIVE);
        dev.setResetRequired(false);
        dev.setRegisteredAt(now);
        dev.setLastSeenAt(now);
        dev.setLastVerifiedAt(now);
        studentDeviceRepository.save(dev);

        auditService.record(user, "DEVICE_REGISTERED", "Device", dev.getId(),
                "Device registered: " + dev.getDeviceName() + " (" + dev.getDevicePlatform() + ")", ipAddress);

        UserSession session = sessionService.open(user, jwtService.accessTokenTtl(), dev.getDeviceName(), ipAddress);
        session.setDeviceId(deviceId);

        String accessToken = jwtService.issueAccessToken(user, session.getTokenId(), session.getExpiresAt(), deviceId);
        auditService.record(user, "DEVICE_LOGIN_SUCCESS", "User", user.getId(), "Logged in from registered device", ipAddress);

        return new LoginResponse(accessToken, "Bearer", session.getExpiresAt(), summarize(user),
                "student-dashboard.html", "AUTHENTICATED", deviceId, deviceSecret, null, null, null, "Device registered successfully.");
    }

    /**
     * Authorizes another browser (Edge / Firefox) on the same registered computer using email OTP.
     */
    @Transactional
    public LoginResponse completeBrowserLink(String tempToken, String otp, String publicKey,
                                            String devicePlatform, String ipAddress) {
        User user = parseTempToken(tempToken, "LINK_BROWSER");
        verifyAndConsumeOtp(user, "LINK_BROWSER", otp, ipAddress);

        StudentDevice dev = studentDeviceRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No registered device found for this account."));

        if (!dev.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your registered device is not active.");
        }

        // Generate a fresh device credential for this authorized browser
        String newDeviceSecret = TokenUtil.generateOpaqueToken();
        dev.setDeviceSecretHash(TokenUtil.sha256Hex(newDeviceSecret));
        dev.setLastSeenAt(Instant.now());
        dev.setLastVerifiedAt(Instant.now());
        if (publicKey != null && !publicKey.isBlank()) {
            dev.setDevicePublicKey(publicKey);
        }
        studentDeviceRepository.save(dev);

        UserSession session = sessionService.open(user, jwtService.accessTokenTtl(), dev.getDeviceName(), ipAddress);
        session.setDeviceId(dev.getDeviceId());

        String accessToken = jwtService.issueAccessToken(user, session.getTokenId(), session.getExpiresAt(), dev.getDeviceId());
        auditService.record(user, "DEVICE_LOGIN_SUCCESS", "User", user.getId(),
                "Authorized secondary browser on registered device: " + dev.getDeviceName(), ipAddress);

        return new LoginResponse(accessToken, "Bearer", session.getExpiresAt(), summarize(user),
                "student-dashboard.html", "AUTHENTICATED", dev.getDeviceId(), newDeviceSecret, null, null, null, "Browser authorized on your registered device.");
    }

    /**
     * Student self-service device reset request (e.g. when physical laptop is replaced or lost).
     */
    @Transactional
    public String initiateDeviceReset(String identifier, String ipAddress) {
        User user = userRepository.findByEmailIgnoreCase(identifier.trim())
                .or(() -> studentProfileRepository.findByStudentIdIgnoreCase(identifier.trim()).map(StudentProfile::getUser))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student account found with that identifier."));

        if (user.getRole() != Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only student accounts have device bindings.");
        }

        String tempToken = jwtService.issueTempDeviceToken(user.getId(), "DEVICE_RESET", TEMP_TOKEN_TTL);
        issueAndSendOtp(user, "DEVICE_RESET", null, "Reset Request", ipAddress, "device reset / replacement");
        auditService.record(user, "DEVICE_RESET_REQUESTED", "User", user.getId(), "Student requested device reset", ipAddress);
        return tempToken;
    }

    /**
     * Confirms device reset with email OTP, invalidating the old device and all active sessions.
     */
    @Transactional
    public void confirmDeviceReset(String tempToken, String otp, String ipAddress) {
        User user = parseTempToken(tempToken, "DEVICE_RESET");
        verifyAndConsumeOtp(user, "DEVICE_RESET", otp, ipAddress);

        studentDeviceRepository.deleteByUserId(user.getId());
        sessionService.revokeAll(user.getId(), SessionRevokeReason.DEVICE_RESET);

        auditService.record(user, "DEVICE_RESET", "User", user.getId(),
                "Device reset confirmed by student. All sessions revoked; new device registration required on next login.", ipAddress);
    }

    /**
     * Admin device reset: invalidates student's device registration and revokes all active sessions.
     */
    @Transactional
    public void adminResetDevice(Long studentUserId, User adminUser, String ipAddress) {
        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));

        studentDeviceRepository.deleteByUserId(student.getId());
        sessionService.revokeAll(student.getId(), SessionRevokeReason.DEVICE_RESET);

        auditService.record(adminUser, "DEVICE_RESET", "Student", student.getId(),
                "Admin " + adminUser.getEmail() + " reset device for student " + student.getEmail(), ipAddress);
    }

    /**
     * Validates that an incoming request's device headers match the student's active registered device.
     */
    @Transactional(readOnly = true)
    public boolean verifyDeviceToken(Long userId, String deviceId, String deviceToken) {
        if (userId == null || deviceId == null || deviceToken == null) {
            return false;
        }
        Optional<StudentDevice> devOpt = studentDeviceRepository.findByUserId(userId);
        if (devOpt.isEmpty()) {
            return false;
        }
        StudentDevice dev = devOpt.get();
        return dev.isActive()
                && dev.getDeviceId().equals(deviceId)
                && dev.getDeviceSecretHash().equals(TokenUtil.sha256Hex(deviceToken));
    }

    /**
     * Validates that the student has an active registered device matching the given deviceId.
     */
    @Transactional(readOnly = true)
    public boolean validateActiveDevice(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return false;
        }
        return studentDeviceRepository.findByUserId(userId)
                .filter(StudentDevice::isActive)
                .map(d -> d.getDeviceId().equals(deviceId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<StudentDeviceResponse> getStudentDevice(Long studentUserId) {
        return studentDeviceRepository.findByUserId(studentUserId).map(d -> {
            User u = d.getUser();
            StudentProfile profile = studentProfileRepository.findByUserId(u.getId()).orElse(null);
            return new StudentDeviceResponse(
                    u.getId(),
                    profile != null ? profile.getStudentId() : "",
                    u.getFullName(),
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getDevicePlatform(),
                    d.getDeviceStatus(),
                    d.isResetRequired(),
                    d.getRegisteredAt(),
                    d.getLastSeenAt(),
                    d.getLastVerifiedAt()
            );
        });
    }

    // ---- Internal Helpers ----------------------------------------------------------------------

    private void issueAndSendOtp(User user, String purpose, String targetDeviceId, String deviceInfo,
                                 String ipAddress, String actionDescription) {
        otpRepository.deleteByUserIdAndPurpose(user.getId(), purpose);

        String rawOtp = TokenUtil.generate6DigitOtp();
        latestOtpCache.put(user.getId(), rawOtp);
        DeviceVerificationOtp otp = new DeviceVerificationOtp();
        otp.setUser(user);
        otp.setOtpHash(TokenUtil.sha256Hex(rawOtp));
        otp.setPurpose(purpose);
        otp.setTargetDeviceId(targetDeviceId);
        otp.setDeviceName(cleanDeviceName(null, deviceInfo));
        otp.setDevicePlatform(cleanDevicePlatform(deviceInfo));
        otp.setExpiresAt(Instant.now().plus(OTP_TTL));
        otpRepository.save(otp);

        log.info("[DEVICE OTP] Verification code generated for student {} (purpose: {}): OTP={}",
                user.getEmail(), purpose, rawOtp);

        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (ccl != null) {
                Thread.currentThread().setContextClassLoader(ccl);
            }
            try {
                emailService.sendDeviceOtp(user, rawOtp, actionDescription, ipAddress);
            } catch (Exception ex) {
                log.warn("[DEVICE OTP] Could not send OTP email to {}: {}", user.getEmail(), ex.getMessage());
            }
        });
    }

    private void verifyAndConsumeOtp(User user, String purpose, String rawOtp, String ipAddress) {
        DeviceVerificationOtp otp = otpRepository
                .findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), purpose, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code."));

        if (otp.getAttempts() >= 5) {
            otpRepository.delete(otp);
            latestOtpCache.remove(user.getId());
            auditService.record(user, "DEVICE_VERIFICATION_FAILED", "User", user.getId(), "Too many failed OTP attempts", ipAddress);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Please request a new verification code.");
        }

        if (!otp.getOtpHash().equals(TokenUtil.sha256Hex(rawOtp.trim()))) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            auditService.record(user, "DEVICE_VERIFICATION_FAILED", "User", user.getId(), "Incorrect OTP entered", ipAddress);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect verification code. Please try again.");
        }

        otp.setUsedAt(Instant.now());
        otpRepository.save(otp);
        latestOtpCache.remove(user.getId());
    }

    private User parseTempToken(String tempToken, String expectedPurpose) {
        if (tempToken == null || tempToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing device verification token.");
        }
        Optional<Claims> claimsOpt = jwtService.parse(tempToken);
        if (claimsOpt.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired device verification token.");
        }
        Claims claims = claimsOpt.get();
        String purpose = claims.get("purpose", String.class);
        if (purpose == null || !purpose.equals(expectedPurpose)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid verification token purpose.");
        }
        Long userId = Long.valueOf(claims.getSubject());
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User account is not valid."));
    }

    private String cleanDeviceName(String rawName, String platform) {
        if (rawName != null && !rawName.isBlank()) {
            return rawName.trim().length() > 100 ? rawName.trim().substring(0, 100) : rawName.trim();
        }
        if (platform != null && !platform.isBlank()) {
            String lower = platform.toLowerCase();
            if (lower.contains("win")) return "Windows PC";
            if (lower.contains("mac")) return "MacBook / Mac";
            if (lower.contains("linux")) return "Linux PC";
        }
        return "Primary Computer";
    }

    private String cleanDevicePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "Unknown Platform";
        }
        String p = platform.trim();
        return p.length() > 500 ? p.substring(0, 500) : p;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "your registered email";
        int at = email.indexOf("@");
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) return prefix + "***" + domain;
        return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + domain;
    }

    private UserSummary summarize(User user) {
        StudentProfile p = studentProfileRepository.findByUserId(user.getId()).orElse(null);
        return new UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getMobile(),
                user.getRole(),
                p != null ? p.getStudentId() : null,
                user.getLastLoginAt()
        );
    }
}
