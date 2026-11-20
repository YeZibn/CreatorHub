package com.creatorhub.api.config;

import java.io.IOException;

import com.creatorhub.api.identity.IdentityError;
import io.lettuce.core.RedisException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

final class SessionStoreFailureFilter extends OncePerRequestFilter {
    private final ObjectMapper json;

    SessionStoreFailureFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException ex) {
            if (response.isCommitted() || !isRedisFailure(ex)) {
                throw ex;
            }
            response.reset();
            IdentityError.SESSION_UNAVAILABLE.write(response, json);
        }
    }

    private boolean isRedisFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof RedisSystemException
                    || current instanceof RedisException) {
                return true;
            }
        }
        return false;
    }
}
