package com.gyanwire.auth.api;

import com.gyanwire.auth.AuthCookieService;
import com.gyanwire.auth.AuthException;
import com.gyanwire.auth.AuthService;
import com.gyanwire.controller.dto.StandardApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final ConcurrentHashMap<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> login(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        rateLimit(request);
        String email = str(body, "EmailId", "emailId", "email");
        String password = str(body, "Password", "password");
        Map<String, Object> session = authService.loginWithPassword(email, password);
        cookieService.setAuthCookies(response,
                (String) session.get("accessToken"),
                (String) session.get("refreshToken"),
                ((Number) session.get("expiresInSeconds")).longValue(),
                ((Number) session.get("refreshExpiresInSeconds")).longValue());
        return ResponseEntity.ok(StandardApiResponse.success("Signed in successfully.", AuthService.publicSession(session)));
    }

    @PostMapping("/google-login")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> googleLogin(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        rateLimit(request);
        String accessToken = str(body, "AccessToken", "accessToken");
        if (accessToken.isBlank()) {
            throw new AuthException("Google access token is required.", "AUTH_GOOGLE_TOKEN_MISSING", 400);
        }
        Map<String, Object> session = authService.loginWithGoogle(accessToken);
        cookieService.setAuthCookies(response,
                (String) session.get("accessToken"),
                (String) session.get("refreshToken"),
                ((Number) session.get("expiresInSeconds")).longValue(),
                ((Number) session.get("refreshExpiresInSeconds")).longValue());
        return ResponseEntity.ok(StandardApiResponse.success("Signed in with Google.", AuthService.publicSession(session)));
    }

    @PostMapping("/register")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        Map<String, Object> data = authService.register(
                str(body, "EmailId", "emailId", "email"),
                str(body, "Password", "password"),
                str(body, "FirstName", "firstName"),
                str(body, "LastName", "lastName")
        );
        return ResponseEntity.status(201).body(StandardApiResponse.success("Account created. You can sign in now.", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> refresh(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refresh = body == null ? "" : str(body, "RefreshToken", "refreshToken");
        if (refresh.isBlank()) refresh = cookieService.readRefreshToken(request);
        try {
            Map<String, Object> session = authService.refreshSession(refresh);
            cookieService.setAuthCookies(response,
                    (String) session.get("accessToken"),
                    (String) session.get("refreshToken"),
                    ((Number) session.get("expiresInSeconds")).longValue(),
                    ((Number) session.get("refreshExpiresInSeconds")).longValue());
            return ResponseEntity.ok(StandardApiResponse.success("Session refreshed.", AuthService.publicSession(session)));
        } catch (AuthException e) {
            cookieService.clearAuthCookies(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<StandardApiResponse<Object>> logout(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refresh = body == null ? "" : str(body, "RefreshToken", "refreshToken");
        if (refresh.isBlank()) refresh = cookieService.readRefreshToken(request);
        authService.logout(refresh);
        cookieService.clearAuthCookies(response);
        return ResponseEntity.ok(StandardApiResponse.success("Signed out.", null));
    }

    @GetMapping("/me")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> me(HttpServletRequest request) {
        Map<String, Object> user = authService.getCurrentUser(cookieService.readAccessToken(request));
        return ResponseEntity.ok(StandardApiResponse.success("OK", user));
    }

    private void rateLimit(HttpServletRequest request) {
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] entry = loginAttempts.compute(ip, (k, prev) -> {
            if (prev == null || now - prev[1] > 60_000) return new long[]{1, now};
            prev[0] += 1;
            return prev;
        });
        if (entry[0] > 5) {
            throw new AuthException("Too many sign-in attempts. Please wait a minute and try again.", "AUTH_RATE_LIMITED", 429);
        }
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return "";
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null) return String.valueOf(v).trim();
        }
        return "";
    }
}
