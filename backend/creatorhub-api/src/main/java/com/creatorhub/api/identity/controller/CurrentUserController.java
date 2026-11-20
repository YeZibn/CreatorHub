package com.creatorhub.api.identity.controller;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.api.identity.dto.IdentityRequests.ProfileRequest;
import com.creatorhub.core.identity.service.AccountService;
import com.creatorhub.core.identity.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    private final AccountService accounts;
    private final UserProfileService profiles;

    public CurrentUserController(AccountService accounts, UserProfileService profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    @GetMapping
    ResponseEntity<CurrentUserResponse> currentUser(@AuthenticationPrincipal IdentityPrincipal principal) {
        long userId = principal.userId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUserResponse(Long.toString(userId), accounts.emailOf(userId),
                        profiles.get(userId).displayName()));
    }

    @PatchMapping(path = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProfileResponse> changeDisplayName(@AuthenticationPrincipal IdentityPrincipal principal,
                                                       @Valid @RequestBody ProfileRequest input) {
        var profile = profiles.changeDisplayName(principal.userId(), input.displayName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ProfileResponse(Long.toString(profile.userId()), profile.displayName()));
    }

    record CurrentUserResponse(String userId, String email, String displayName) {}
    record ProfileResponse(String userId, String displayName) {}
}
