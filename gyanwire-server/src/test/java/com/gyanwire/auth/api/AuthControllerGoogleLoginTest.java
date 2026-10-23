package com.gyanwire.auth.api;

import com.gyanwire.auth.AuthCookieService;
import com.gyanwire.auth.AuthException;
import com.gyanwire.auth.AuthService;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AuthControllerGoogleLoginTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthService authService;

    @MockBean
    AuthCookieService cookieService;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void googleLoginRequiresAccessToken() throws Exception {
        mvc.perform(post("/api/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_GOOGLE_TOKEN_MISSING"));
    }

    @Test
    void googleLoginAcceptsAccessTokenAliasAndSetsCookies() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.loginWithGoogle("ya29.abc")).thenReturn(session(userId, "g@example.com", "access-jwt", "refresh-jwt"));

        mvc.perform(post("/api/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"ya29.abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("g@example.com"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(cookieService).setAuthCookies(any(), eq("access-jwt"), eq("refresh-jwt"), eq(3600L), eq(86400L));
    }

    @Test
    void googleLoginAcceptsPascalCaseAccessToken() throws Exception {
        when(authService.loginWithGoogle("tok")).thenReturn(session(UUID.randomUUID(), "g@example.com", "a", "r"));

        mvc.perform(post("/api/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"AccessToken\":\"tok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Signed in with Google."));
    }

    @Test
    void googleLoginSurfacesAuthFailure() throws Exception {
        when(authService.loginWithGoogle(anyString()))
                .thenThrow(new AuthException("Google sign-in failed.", "AUTH_GOOGLE_FAILED", 401));

        mvc.perform(post("/api/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"AccessToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_GOOGLE_FAILED"));
    }

    private static Map<String, Object> session(UUID userId, String email, String access, String refresh) {
        Map<String, Object> out = new HashMap<>();
        out.put("userId", userId);
        out.put("email", email);
        out.put("firstName", "G");
        out.put("lastName", "User");
        out.put("username", "G User");
        out.put("role", "user");
        out.put("profilePic", "");
        out.put("accessToken", access);
        out.put("refreshToken", refresh);
        out.put("expiresInSeconds", 3600L);
        out.put("refreshExpiresInSeconds", 86400L);
        return out;
    }
}
