package com.creatorhub.api.identity.dto;

import com.creatorhub.core.identity.service.AccountService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class IdentityRequests {
    private IdentityRequests() {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[!-~]+") String email,
            @NotNull @Size(min = 1, max = 256) String password) {

        public LoginRequest {
            email = AccountService.normalizeEmail(email);
        }

        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[!-~]+") String email,
            @NotNull @Size(max = 256) String password,
            @NotBlank String displayName) {

        public RegisterRequest {
            email = AccountService.normalizeEmail(email);
            displayName = strip(displayName);
        }

        @AssertTrue
        public boolean isPasswordLengthValid() {
            return hasLength(password, 15, 128);
        }

        @AssertTrue
        public boolean isDisplayNameLengthValid() {
            return hasLength(displayName, 1, 50);
        }

        @Override
        public String toString() {
            return "RegisterRequest[redacted]";
        }
    }

    public record ProfileRequest(@NotBlank String displayName) {
        public ProfileRequest {
            displayName = strip(displayName);
        }

        @AssertTrue
        public boolean isDisplayNameLengthValid() {
            return hasLength(displayName, 1, 50);
        }
    }

    private static boolean hasLength(String value, int min, int max) {
        if (value == null) {
            return false;
        }
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
