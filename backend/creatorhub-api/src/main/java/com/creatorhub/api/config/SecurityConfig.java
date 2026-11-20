package com.creatorhub.api.config;

import java.util.List;

import com.creatorhub.api.identity.IdentityError;
import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.core.identity.service.AccountService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            HttpSessionSecurityContextRepository securityContexts,
            HttpSessionCsrfTokenRepository csrfTokens,
            ObjectMapper json) throws Exception {
        return http
                .securityContext(context -> context.securityContextRepository(securityContexts))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                IdentityError.UNAUTHENTICATED.write(response, json))
                        .accessDeniedHandler((request, response, error) -> {
                            if (error instanceof CsrfException) {
                                IdentityError.CSRF_INVALID.write(response, json);
                            } else {
                                IdentityError.ACCESS_DENIED.write(response, json);
                            }
                        }))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    AuthenticationManager identityAuthenticationManager(AccountService accounts) {
        return request -> {
            long userId = accounts.authenticate(request.getName(), (String) request.getCredentials())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            return UsernamePasswordAuthenticationToken.authenticated(new IdentityPrincipal(userId), null, List.of());
        };
    }

    @Bean
    HttpSessionSecurityContextRepository securityContexts() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokens() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(HttpSessionCsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    SecurityContextLogoutHandler logoutHandler(HttpSessionSecurityContextRepository securityContexts) {
        var handler = new SecurityContextLogoutHandler();
        handler.setSecurityContextRepository(securityContexts);
        return handler;
    }

    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${creatorhub.identity.secure-cookie}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("CREATORHUB_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    FilterRegistrationBean<SessionStoreFailureFilter> sessionStoreFailureFilter(ObjectMapper json) {
        var registration = new FilterRegistrationBean<>(new SessionStoreFailureFilter(json));
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }
}
