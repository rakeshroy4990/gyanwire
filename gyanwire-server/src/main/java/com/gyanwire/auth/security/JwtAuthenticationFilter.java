package com.gyanwire.auth.security;

import com.gyanwire.auth.AuthCookieService;
import com.gyanwire.auth.JwtService;
import com.gyanwire.persistence.postgres.model.UserEntity;
import com.gyanwire.persistence.postgres.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookieService cookieService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(AuthCookieService cookieService, JwtService jwtService, UserRepository userRepository) {
        this.cookieService = cookieService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = cookieService.readAccessToken(request);
            if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = jwtService.parseAndValidate(token);
                if ("access".equalsIgnoreCase(String.valueOf(claims.get("tokenType")))) {
                    UUID userId = UUID.fromString(claims.getSubject());
                    UserEntity user = userRepository.findActiveById(userId).orElse(null);
                    if (user != null
                            && ((Number) claims.get("tokenVersion")).longValue() == user.getTokenVersion()) {
                        AuthUserPrincipal principal = new AuthUserPrincipal(user.getId(), user.getEmail(), user.getRole());
                        var auth = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + (user.getRole() == null ? "USER" : user.getRole().toUpperCase())))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
