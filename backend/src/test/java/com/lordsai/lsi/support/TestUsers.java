package com.lordsai.lsi.support;

import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Shared fixture helpers for integration tests. */
@Component
public class TestUsers {

    public static final String PASSWORD = "Passw0rd123";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(UserRepository userRepository,
                     StudentProfileRepository studentProfileRepository,
                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(String email, Role role, AccountStatus status) {
        User user = new User();
        user.setFullName(role.name().charAt(0) + role.name().substring(1).toLowerCase() + " User");
        user.setEmail(email.toLowerCase());
        user.setMobile("9876543210");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setAccountStatus(status);
        return userRepository.saveAndFlush(user);
    }

    public User student(String email) {
        User user = create(email, Role.STUDENT, AccountStatus.ACTIVE);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setStudentId("LSI-TEST-" + user.getId());
        profile.setRegistrationDate(LocalDate.now());
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    /** A legacy TEACHER row — the role is retired, so such an account must be refused everywhere. */
    @SuppressWarnings("deprecation")
    public User legacyTeacher(String email) {
        return create(email, Role.TEACHER, AccountStatus.ACTIVE);
    }

    public User admin(String email) {
        return create(email, Role.ADMIN, AccountStatus.ACTIVE);
    }
}
