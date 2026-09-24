package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** Account creation and profile management shared by the purchase flow and the admin portal. */
@Service
public class UserService {

    public record StudentCreation(User user, StudentProfile profile, boolean created, String temporaryPassword) {
    }

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentIdService studentIdService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       StudentIdService studentIdService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.studentIdService = studentIdService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Reuses an existing student with the same email, otherwise creates one in PENDING_SETUP
     * with a generated student ID. Never creates a duplicate account for a repeat purchase.
     */
    @Transactional
    public StudentCreation findOrCreateStudent(String fullName, String email, String mobile) {
        String cleanEmail = AuthService.normalizeEmail(email);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(cleanEmail);

        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.STUDENT) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "This email belongs to a staff account. Please use a different email for enrollment.");
            }
            StudentProfile profile = studentProfileRepository.findByUserId(user.getId())
                    .orElseGet(() -> attachProfile(user, null, null));
            return new StudentCreation(user, profile, false, null);
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setMobile(cleanMobile(mobile));
        user.setRole(Role.STUDENT);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);

        StudentProfile profile = attachProfile(user, null, null);
        String temporaryPassword = assignGeneratedPassword(user);
        return new StudentCreation(user, profile, true, temporaryPassword);
    }

    @Transactional
    public StudentCreation createStudentByAdmin(String fullName, String email, String mobile,
                                                String batch, String location) {
        String cleanEmail = AuthService.normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setMobile(cleanMobile(mobile));
        user.setRole(Role.STUDENT);
        user.setAccountStatus(AccountStatus.PENDING_SETUP);
        user = userRepository.save(user);
        return new StudentCreation(user, attachProfile(user, batch, location), true, null);
    }

    @Transactional
    public void updateContact(User user, String fullName, String email, String mobile) {
        String cleanEmail = AuthService.normalizeEmail(email);
        if (!cleanEmail.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another account already uses this email.");
        }
        user.setFullName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setMobile(cleanMobile(mobile));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(AuthService.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Transactional(readOnly = true)
    public User requireUser(Long id, Role role) {
        User user = requireUser(id);
        if (user.getRole() != role) {
            throw ResourceNotFoundException.of(role.name().toLowerCase(), id);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudentProfile(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", userId));
    }

    private StudentProfile attachProfile(User user, String batch, String location) {
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setStudentId(studentIdService.next());
        profile.setBatch(batch);
        profile.setLocation(location);
        profile.setRegistrationDate(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        return studentProfileRepository.save(profile);
    }

    private String assignGeneratedPassword(User user) {
        String temporaryPassword = com.lordsai.lsi.util.TokenUtil.generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        return temporaryPassword;
    }

    /** Generates a replacement temporary password without ever reading one from storage. */
    @Transactional
    public String issueReplacementPassword(User user) {
        return assignGeneratedPassword(user);
    }

    public static String cleanMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() > 10 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
    }
}
