package com.creatorhub.api.social.controller;

import com.creatorhub.api.identity.IdentityError;
import com.creatorhub.core.social.service.FollowService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FollowController.class)
public class FollowExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<IdentityError.Body> invalidRequest() {
        return IdentityError.INVALID_REQUEST.toResponse();
    }

    @ExceptionHandler(FollowService.TargetNotFound.class)
    ResponseEntity<IdentityError.Body> targetNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore())
                .body(new IdentityError.Body("FOLLOW_TARGET_NOT_FOUND", "Follow target not found"));
    }
}
