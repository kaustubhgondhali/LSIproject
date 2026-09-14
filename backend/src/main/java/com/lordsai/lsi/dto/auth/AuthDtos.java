package com.lordsai.lsi.dto.auth;

import com.lordsai.lsi.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public static final String PASSWORD_RULE_MESSAGE =
            "Password must be 8-72 characters and contain at least one letter and one number.";
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    public record LoginRequest(
            @NotBlank(message = "Enter your Student ID or email.") String identifier,
            @NotBlank(message = "Enter your password.") String password,
            /** Which login screen sent this: "STUDENT" (Student Admin) or "ADMIN" (Admin Login).
             *  The server refuses the login when the account's role does not belong to that portal. */
            String portal
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            UserSummary user,
            String redirectUrl
    ) {
    }

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            String mobile,
            Role role,
            String studentId,
            Instant lastLoginAt
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email(message = "Enter a valid email address.") String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 8, max = 72, message = PASSWORD_RULE_MESSAGE)
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_RULE_MESSAGE)
            String newPassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 8, max = 72, message = PASSWORD_RULE_MESSAGE)
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_RULE_MESSAGE)
            String newPassword
    ) {
    }

    public record TokenCheckResponse(boolean valid, String email, String purpose) {
    }
}
