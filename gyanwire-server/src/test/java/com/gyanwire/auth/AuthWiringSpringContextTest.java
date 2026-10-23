package com.gyanwire.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.auth.api.AuthController;
import com.gyanwire.persistence.postgres.repository.RefreshTokenRepository;
import com.gyanwire.persistence.postgres.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Startup smoke for auth beans. Mocked unit tests cannot catch missing/ambiguous
 * Spring constructors — this context load can.
 */
@SpringJUnitConfig(AuthWiringSpringContextTest.Config.class)
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-at-least-16-chars",
        "app.auth.jwt.access-expiration-seconds=3600",
        "app.auth.jwt.refresh-expiration-seconds=86400",
        "app.auth.cookie.access-token-name=access_token",
        "app.auth.cookie.refresh-token-name=refresh_token",
        "app.auth.cookie.refresh-path=/api/auth",
        "app.auth.cookie.secure=false",
        "app.auth.cookie.same-site=Lax",
        "app.auth.cookie.partitioned=true",
        "app.auth.cookie.domain="
})
class AuthWiringSpringContextTest {

    @Configuration
    @Import({
            GoogleAuthClient.class,
            AuthService.class,
            JwtService.class,
            AuthCookieService.class,
            AuthController.class
    })
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        RefreshTokenRepository refreshTokenRepository() {
            return mock(RefreshTokenRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired GoogleAuthClient googleAuthClient;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired AuthCookieService authCookieService;
    @Autowired AuthController authController;

    @Test
    void authStackConstructsInSpring() {
        assertThat(googleAuthClient).isNotNull();
        assertThat(authService).isNotNull();
        assertThat(jwtService).isNotNull();
        assertThat(authCookieService).isNotNull();
        assertThat(authController).isNotNull();
    }
}
