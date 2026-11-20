package com.creatorhub.api.config;

import com.creatorhub.api.identity.IdentityError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<IdentityError.Body> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer errorStatus ? errorStatus : 404;
        IdentityError.Body body = status >= 500
                ? new IdentityError.Body("INTERNAL_ERROR", "Unable to complete the request")
                : new IdentityError.Body("HTTP_" + status, "Request failed");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
