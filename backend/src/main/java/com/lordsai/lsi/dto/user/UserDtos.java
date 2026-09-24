package com.lordsai.lsi.dto.user;

import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class UserDtos {

    private UserDtos() {
    }

    public static final String MOBILE_PATTERN = "^[6-9]\\d{9}$";
    public static final String MOBILE_MESSAGE = "Enter a valid 10-digit Indian mobile number.";

    public record CreateStudentRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE) String mobile,
            @Size(max = 100) String batch,
            @Size(max = 150) String location,
            /** True: generate a temporary password, activate the account and email the credentials.
             *  False/absent (default): email a one-time password setup link instead. */
            Boolean sendCredentials
    ) {
        public boolean wantsCredentials() {
            return Boolean.TRUE.equals(sendCredentials);
        }
    }

    /** Outcome of creating a student, with a message that reflects what was actually emailed. */
    public record StudentCreated(StudentResponse student, String message, boolean emailDelivered) {
    }

    public record UpdateStudentRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE) String mobile,
            @Size(max = 100) String batch,
            @Size(max = 150) String location
    ) {
    }

    public record StudentResponse(
            Long id,
            String studentId,
            String fullName,
            String email,
            String mobile,
            String batch,
            String location,
            AccountStatus accountStatus,
            LocalDate registrationDate,
            Instant lastLoginAt,
            Instant createdAt,
            long enrolledCourses,
            boolean hasActiveSession
    ) {
    }

    public record UserLite(Long id, String fullName, String email, Role role) {
    }
}
