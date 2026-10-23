package com.gyanwire.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {

    @Test
    void sameSiteNoneAddsPartitionedAndSecure() {
        AuthCookieService cookies = new AuthCookieService(
                "access_token", "refresh_token", "/api/auth",
                false, "None", true, "");
        ResponseCookie cookie = cookies.build("access_token", "tok", "/", 60);
        String header = cookie.toString();
        assertTrue(header.contains("Secure"), header);
        assertTrue(header.contains("SameSite=None"), header);
        assertTrue(header.contains("Partitioned"), header);
    }

    @Test
    void sameSiteLaxSkipsPartitioned() {
        AuthCookieService cookies = new AuthCookieService(
                "access_token", "refresh_token", "/api/auth",
                false, "Lax", true, "");
        ResponseCookie cookie = cookies.build("access_token", "tok", "/", 60);
        String header = cookie.toString();
        assertFalse(header.contains("Partitioned"), header);
        assertTrue(header.contains("SameSite=Lax"), header);
    }

    @Test
    void secureFlagHonoredOnLax() {
        AuthCookieService cookies = new AuthCookieService(
                "access_token", "refresh_token", "/api/auth",
                true, "Lax", true, "");
        ResponseCookie cookie = cookies.build("access_token", "tok", "/", 60);
        assertTrue(cookie.toString().contains("Secure"), cookie.toString());
    }
}
