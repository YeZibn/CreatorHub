package com.creatorhub.api.identity.controller;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.api.identity.dto.IdentityRequests.LoginRequest;
import com.creatorhub.api.identity.dto.IdentityRequests.RegisterRequest;
import com.creatorhub.core.identity.entity.UserProfile;
import com.creatorhub.core.identity.service.AccountService;
import com.creatorhub.core.identity.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AccountService accounts;
    private final UserProfileService profiles;
    private final SessionAuthenticationStrategy loginSessionStrategy;
    private final HttpSessionSecurityContextRepository securityContexts;
    private final SecurityContextLogoutHandler logoutHandler;

    public AuthController(AuthenticationManager authenticationManager, AccountService accounts,
                          UserProfileService profiles, SessionAuthenticationStrategy loginSessionStrategy,
                          HttpSessionSecurityContextRepository securityContexts, SecurityContextLogoutHandler logoutHandler) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.profiles = profiles;
        this.loginSessionStrategy = loginSessionStrategy;
        this.securityContexts = securityContexts;
        this.logoutHandler = logoutHandler;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest input) {
        long userId = accounts.register(input.email(), input.password(), input.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(Long.toString(userId)));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    LoginResponse login(@Valid @RequestBody LoginRequest input,
                        HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        var principal = (IdentityPrincipal) authentication.getPrincipal();
        UserProfile profile = profiles.get(principal.userId());

        loginSessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContexts.saveContext(context, request, response);
        SecurityContextHolder.setContext(context);

        return new LoginResponse(Long.toString(principal.userId()), profile.displayName());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    record CsrfResponse(String headerName, String token) {}
    record RegistrationResponse(String userId) {}
    record LoginResponse(String userId, String displayName) {}
}
