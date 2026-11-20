package com.creatorhub.api.content;

import com.creatorhub.api.identity.IdentityError;
import com.creatorhub.core.content.service.DraftService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {DraftController.class, ArticleController.class, MyArticlesController.class})
public class DraftExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<IdentityError.Body> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(DraftService.NotFound.class)
    ResponseEntity<IdentityError.Body> notFound() {
        return error(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "Draft not found");
    }

    @ExceptionHandler(DraftService.VersionConflict.class)
    ResponseEntity<IdentityError.Body> versionConflict() {
        return error(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "Draft version conflict");
    }

    @ExceptionHandler(DraftService.InvalidForPublishing.class)
    ResponseEntity<IdentityError.Body> invalidForPublishing() {
        return invalidRequest();
    }

    @ExceptionHandler(DraftService.ArticleNotFound.class)
    ResponseEntity<IdentityError.Body> articleNotFound() {
        return error(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "Article not found");
    }

    private ResponseEntity<IdentityError.Body> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new IdentityError.Body(code, message));
    }
}
