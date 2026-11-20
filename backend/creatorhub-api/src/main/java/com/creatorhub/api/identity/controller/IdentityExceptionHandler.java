package com.creatorhub.api.identity.controller;

import com.creatorhub.api.identity.IdentityError;
import com.creatorhub.core.identity.service.EmailAlreadyRegisteredException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class IdentityExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<IdentityError.Body> invalidRequest() {
        return IdentityError.INVALID_REQUEST.toResponse();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<IdentityError.Body> invalidCredentials() {
        return IdentityError.INVALID_CREDENTIALS.toResponse();
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<IdentityError.Body> duplicateEmail() {
        return IdentityError.EMAIL_ALREADY_REGISTERED.toResponse();
    }
}
