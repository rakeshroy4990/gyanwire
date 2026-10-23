package com.gyanwire.auth;

import com.gyanwire.persistence.postgres.model.UserEntity;
import com.gyanwire.persistence.postgres.repository.RefreshTokenRepository;
import com.gyanwire.persistence.postgres.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleLoginTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GoogleAuthClient googleAuthClient;

    @InjectMocks AuthService authService;

    @Test
    void loginWithGoogleRejectsInvalidToken() {
        when(googleAuthClient.fetchVerifiedProfile("bad")).thenReturn(null);

        assertThatThrownBy(() -> authService.loginWithGoogle("bad"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo("AUTH_GOOGLE_FAILED");
    }

    @Test
    void loginWithGoogleCreatesUserWhenMissing() {
        when(googleAuthClient.fetchVerifiedProfile("tok")).thenReturn(
                new GoogleAuthClient.GoogleProfile("new@example.com", "New", "User", "https://pic"));
        when(userRepository.findActiveByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(jwtService.generateAccessToken(any(), anyString(), anyLong())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(), anyLong())).thenReturn("refresh");
        when(jwtService.getAccessSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshSeconds()).thenReturn(86400L);

        Map<String, Object> session = authService.loginWithGoogle("tok");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthProvider()).isEqualTo("google");
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(session.get("email")).isEqualTo("new@example.com");
        assertThat(session.get("accessToken")).isEqualTo("access");
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void loginWithGoogleReusesExistingUserAndFillsBlankNames() {
        UUID id = UUID.randomUUID();
        UserEntity existing = new UserEntity();
        existing.setId(id);
        existing.setEmail("old@example.com");
        existing.setFirstName("");
        existing.setLastName("");
        existing.setProfilePic("");
        existing.setRole("user");
        existing.setTokenVersion(1L);

        when(googleAuthClient.fetchVerifiedProfile("tok")).thenReturn(
                new GoogleAuthClient.GoogleProfile("old@example.com", "Old", "Hand", "https://pic"));
        when(userRepository.findActiveByEmail("old@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(eqId(id), anyString(), anyLong())).thenReturn("access");
        when(jwtService.generateRefreshToken(eqId(id), anyLong())).thenReturn("refresh");
        when(jwtService.getAccessSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshSeconds()).thenReturn(86400L);

        Map<String, Object> session = authService.loginWithGoogle("tok");

        assertThat(session.get("firstName")).isEqualTo("Old");
        assertThat(session.get("lastName")).isEqualTo("Hand");
        assertThat(session.get("profilePic")).isEqualTo("https://pic");
        verify(userRepository).save(existing);
    }

    @Test
    void loginWithGoogleDoesNotOverwriteExistingNames() {
        UUID id = UUID.randomUUID();
        UserEntity existing = new UserEntity();
        existing.setId(id);
        existing.setEmail("keep@example.com");
        existing.setFirstName("Keep");
        existing.setLastName("Me");
        existing.setProfilePic("https://old");
        existing.setRole("user");
        existing.setTokenVersion(1L);

        when(googleAuthClient.fetchVerifiedProfile("tok")).thenReturn(
                new GoogleAuthClient.GoogleProfile("keep@example.com", "New", "Name", "https://new"));
        when(userRepository.findActiveByEmail("keep@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(eqId(id), anyString(), anyLong())).thenReturn("access");
        when(jwtService.generateRefreshToken(eqId(id), anyLong())).thenReturn("refresh");
        when(jwtService.getAccessSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshSeconds()).thenReturn(86400L);

        authService.loginWithGoogle("tok");

        verify(userRepository, never()).save(any());
        assertThat(existing.getFirstName()).isEqualTo("Keep");
        assertThat(existing.getProfilePic()).isEqualTo("https://old");
    }

    private static UUID eqId(UUID id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }
}
