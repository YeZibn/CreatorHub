package com.creatorhub.api.identity;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

public enum IdentityError {
    INVALID_REQUEST(400, "Invalid request"),
    UNAUTHENTICATED(401, "Authentication required"),
    INVALID_CREDENTIALS(401, "Invalid email or password"),
    CSRF_INVALID(403, "Invalid CSRF token"),
    ACCESS_DENIED(403, "Access denied"),
    EMAIL_ALREADY_REGISTERED(409, "Email already registered"),
    SESSION_UNAVAILABLE(503, "Session service unavailable");

    private final int status;
    private final String message;

    IdentityError(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public ResponseEntity<Body> toResponse() {
        return ResponseEntity.status(status).body(body());
    }

    public void write(HttpServletResponse response, ObjectMapper json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(body()));
    }

    private Body body() {
        return new Body(name(), message);
    }

    public record Body(String code, String message) {}
}
