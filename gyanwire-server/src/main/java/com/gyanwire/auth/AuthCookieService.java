package com.gyanwire.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {

    private final String accessName;
    private final String refreshName;
    private final String refreshPath;
    private final boolean secure;
    private final String sameSite;
    private final String domain;

    public AuthCookieService(
            @Value("${app.auth.cookie.access-token-name}") String accessName,
            @Value("${app.auth.cookie.refresh-token-name}") String refreshName,
            @Value("${app.auth.cookie.refresh-path}") String refreshPath,
            @Value("${app.auth.cookie.secure}") boolean secure,
            @Value("${app.auth.cookie.same-site}") String sameSite,
            @Value("${app.auth.cookie.domain:}") String domain
    ) {
        this.accessName = accessName;
        this.refreshName = refreshName;
        this.refreshPath = refreshPath;
        this.sameSite = sameSite == null ? "Lax" : sameSite;
        boolean sameSiteNone = this.sameSite.equalsIgnoreCase("None");
        this.secure = sameSiteNone || secure;
        this.domain = domain == null ? "" : domain.trim();
    }

    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken,
                               long accessSeconds, long refreshSeconds) {
        response.addHeader("Set-Cookie", build(accessName, accessToken, "/", accessSeconds).toString());
        response.addHeader("Set-Cookie", build(refreshName, refreshToken, refreshPath, refreshSeconds).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", build(accessName, "", "/", 0).toString());
        response.addHeader("Set-Cookie", build(refreshName, "", refreshPath, 0).toString());
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessName);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshName);
    }

    private ResponseCookie build(String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(Duration.ofSeconds(Math.max(0, maxAgeSeconds)))
                .sameSite(sameSite);
        if (!domain.isEmpty()) {
            b.domain(domain);
        }
        return b.build();
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
