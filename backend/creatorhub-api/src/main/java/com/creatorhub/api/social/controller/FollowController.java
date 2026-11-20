package com.creatorhub.api.social.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.api.social.controller.FollowCursor.Direction;
import com.creatorhub.core.social.entity.FollowDirection;
import com.creatorhub.core.social.entity.FollowSummary;
import com.creatorhub.core.social.service.FollowListService;
import com.creatorhub.core.social.service.FollowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class FollowController {
    private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]{1,19}");
    private final FollowService follows;
    private final FollowListService lists;

    public FollowController(FollowService follows, FollowListService lists) {
        this.follows = follows;
        this.lists = lists;
    }

    @PutMapping("/following/{userId}")
    ResponseEntity<Void> follow(@AuthenticationPrincipal IdentityPrincipal principal,
                                @PathVariable String userId, HttpServletRequest request) throws IOException {
        requireEmptyWrite(request);
        long target = targetId(userId, principal.userId());
        follows.follow(principal.userId(), target);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/following/{userId}")
    ResponseEntity<Void> unfollow(@AuthenticationPrincipal IdentityPrincipal principal,
                                  @PathVariable String userId, HttpServletRequest request) throws IOException {
        requireEmptyWrite(request);
        long target = targetId(userId, principal.userId());
        follows.unfollow(principal.userId(), target);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/following/{userId}")
    ResponseEntity<FollowStatus> status(@AuthenticationPrincipal IdentityPrincipal principal,
                                       @PathVariable String userId) {
        long target = targetId(userId, principal.userId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new FollowStatus(follows.isFollowing(principal.userId(), target)));
    }

    @GetMapping("/following")
    ResponseEntity<Page> following(@AuthenticationPrincipal IdentityPrincipal principal,
                                   @RequestParam(required = false) String limit,
                                   @RequestParam(required = false) String cursor) {
        return page(principal.userId(), limit, cursor, Direction.FOLLOWING);
    }

    @GetMapping("/followers")
    ResponseEntity<Page> followers(@AuthenticationPrincipal IdentityPrincipal principal,
                                   @RequestParam(required = false) String limit,
                                   @RequestParam(required = false) String cursor) {
        return page(principal.userId(), limit, cursor, Direction.FOLLOWERS);
    }

    private ResponseEntity<Page> page(long userId, String limit, String cursor, Direction direction) {
        int size = limit == null ? 20 : Integer.parseInt(limit);
        if (size < 1 || size > 50) throw new IllegalArgumentException();

        FollowCursor after = cursor == null ? null : FollowCursor.decode(cursor, direction);
        LocalDateTime beforeTime = after == null ? null : after.followedAt();
        Long beforeId = after == null ? null : after.userId();
        FollowDirection listDirection = direction == Direction.FOLLOWING
                ? FollowDirection.FOLLOWING : FollowDirection.FOLLOWERS;
        List<FollowSummary> rows = lists.list(userId, listDirection, size + 1, beforeTime, beforeId);
        List<FollowSummary> page = rows.subList(0, Math.min(size, rows.size()));
        String nextCursor = null;
        if (rows.size() > size) {
            FollowSummary last = page.getLast();
            nextCursor = new FollowCursor(last.followedAt(), last.userId()).encode(direction);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Page(page.stream().map(Summary::from).toList(), nextCursor));
    }

    private static long targetId(String value, long userId) {
        if (!DECIMAL_ID.matcher(value).matches()) throw new IllegalArgumentException();
        long target = Long.parseLong(value);
        if (target <= 0 || target == userId) throw new IllegalArgumentException();
        return target;
    }

    private static void requireEmptyWrite(HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null || request.getContentLengthLong() > 0
                || request.getInputStream().read() != -1) {
            throw new IllegalArgumentException();
        }
    }

    record FollowStatus(boolean following) {}

    record Summary(String userId, String displayName, String followedAt) {
        static Summary from(FollowSummary row) {
            return new Summary(Long.toString(row.userId()), row.displayName(),
                    row.followedAt().toInstant(ZoneOffset.UTC).toString());
        }
    }

    record Page(List<Summary> items, String nextCursor) {}
}
