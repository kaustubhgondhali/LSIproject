package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.CourseModule;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.CourseStatus;
import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseModuleRepository moduleRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserSessionRepository sessionRepository;
    @Autowired SiteRepository siteRepository;
    @Autowired StudentIdSequenceRepository sequenceRepository;

    @Test
    void flywaySeedsBothSites() {
        assertThat(siteRepository.findBySiteCode(SiteCode.ACADEMY)).isPresent();
        assertThat(siteRepository.findBySiteCode(SiteCode.MUTUAL_FUND)).isPresent();
        assertThat(siteRepository.findAll()).extracting(Site::getSiteCode)
                .containsExactlyInAnyOrder(SiteCode.ACADEMY, SiteCode.MUTUAL_FUND);
    }

    @Test
    void flywaySeedsFlagshipCourseWithNineModules() {
        Course course = courseRepository.findByCourseCodeIgnoreCase("smet-master").orElseThrow();
        assertThat(course.getCourseName()).isEqualTo("Share Market Education & Training");
        assertThat(course.getPrice()).isEqualByComparingTo(new BigDecimal("14999.00"));
        assertThat(course.effectivePrice()).isEqualByComparingTo(new BigDecimal("9999.00"));
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);

        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByDisplayOrderAsc(course.getId());
        assertThat(modules).hasSize(9);
        assertThat(modules.get(0).getModuleName()).isEqualTo("Stock Market Basics");
        assertThat(modules.get(8).getModuleName()).isEqualTo("Live Market Observation");
    }

    @Test
    void studentIdSequenceIsSeededForCurrentYear() {
        assertThat(sequenceRepository.findForUpdate(2026)).isPresent();
    }

    @Test
    void duplicateEnrollmentIsRejectedByDatabase() {
        User student = newStudent("dup@example.com");
        Course course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();

        enrollmentRepository.saveAndFlush(newEnrollment(student, course));

        assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(newEnrollment(student, course)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Emails are normalised to lowercase by the service layer before they reach the database,
     * so the unique constraint only needs to guard exact duplicates here.
     */
    @Test
    void duplicateEmailIsRejectedByDatabase() {
        newStudent("same@example.com");
        assertThatThrownBy(() -> newStudent("same@example.com").getId())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeSessionLookupIgnoresRevokedAndExpiredSessions() {
        User student = newStudent("session@example.com");
        Instant now = Instant.now();

        UserSession expired = newSession(student, "expired", now.minus(1, ChronoUnit.HOURS));
        sessionRepository.save(expired);

        UserSession revoked = newSession(student, "revoked", now.plus(1, ChronoUnit.HOURS));
        revoked.revoke(com.lordsai.lsi.entity.enums.SessionRevokeReason.USER_LOGOUT);
        sessionRepository.save(revoked);

        assertThat(sessionRepository.findFirstByUserIdAndActiveTrueAndExpiresAtAfter(student.getId(), now))
                .isEmpty();

        sessionRepository.save(newSession(student, "live", now.plus(1, ChronoUnit.HOURS)));

        assertThat(sessionRepository.findFirstByUserIdAndActiveTrueAndExpiresAtAfter(student.getId(), now))
                .isPresent()
                .get().extracting(UserSession::getTokenId).isEqualTo("live");
    }

    private User newStudent(String email) {
        User user = new User();
        user.setFullName("Test Student");
        user.setEmail(email);
        user.setMobile("9999999999");
        user.setPasswordHash("$2a$12$placeholderhashplaceholderhashplaceholderhashplacehold");
        user.setRole(Role.STUDENT);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Enrollment newEnrollment(User student, Course course) {
        Enrollment e = new Enrollment();
        e.setStudent(student);
        e.setCourse(course);
        e.setSource(EnrollmentSource.ADMIN_MANUAL);
        e.setEnrolledAt(Instant.now());
        return e;
    }

    private UserSession newSession(User user, String tokenId, Instant expiresAt) {
        UserSession s = new UserSession();
        s.setUser(user);
        s.setTokenId(tokenId);
        s.setLastActivityAt(Instant.now());
        s.setExpiresAt(expiresAt);
        return s;
    }
}
