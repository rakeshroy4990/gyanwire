package com.gyanwire.auth;

import com.gyanwire.persistence.postgres.model.RefreshTokenEntity;
import com.gyanwire.persistence.postgres.model.UserEntity;
import com.gyanwire.persistence.postgres.repository.RefreshTokenRepository;
import com.gyanwire.persistence.postgres.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthClient googleAuthClient;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            GoogleAuthClient googleAuthClient
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.googleAuthClient = googleAuthClient;
    }

    @Transactional
    public Map<String, Object> loginWithPassword(String emailId, String password) {
        String identity = emailId == null ? "" : emailId.trim().toLowerCase();
        String raw = password == null ? "" : password;
        if (identity.isBlank() || raw.isBlank()) {
            throw new AuthException("Email and password are required.", "AUTH_VALIDATION_FAILED", 400);
        }
        UserEntity user = userRepository.findActiveByEmail(identity)
                .orElseThrow(() -> new AuthException("Invalid email or password", "AUTH_INVALID_CREDENTIALS", 401));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(raw, user.getPasswordHash())) {
            throw new AuthException("Invalid email or password", "AUTH_INVALID_CREDENTIALS", 401);
        }
        return issueSession(user);
    }

    @Transactional
    public Map<String, Object> loginWithGoogle(String accessToken) {
        GoogleAuthClient.GoogleProfile profile = googleAuthClient.fetchVerifiedProfile(accessToken);
        if (profile == null) {
            throw new AuthException("Google sign-in failed.", "AUTH_GOOGLE_FAILED", 401);
        }
        UserEntity user = userRepository.findActiveByEmail(profile.email()).orElse(null);
        if (user == null) {
            user = new UserEntity();
            user.setEmail(profile.email());
            user.setFirstName(profile.givenName());
            user.setLastName(profile.familyName());
            user.setProfilePic(profile.picture());
            user.setAuthProvider("google");
            user.setRole("user");
            user = userRepository.save(user);
        } else {
            boolean dirty = false;
            if ((user.getFirstName() == null || user.getFirstName().isBlank()) && !profile.givenName().isBlank()) {
                user.setFirstName(profile.givenName()); dirty = true;
            }
            if ((user.getLastName() == null || user.getLastName().isBlank()) && !profile.familyName().isBlank()) {
                user.setLastName(profile.familyName()); dirty = true;
            }
            if ((user.getProfilePic() == null || user.getProfilePic().isBlank()) && !profile.picture().isBlank()) {
                user.setProfilePic(profile.picture()); dirty = true;
            }
            if (dirty) user = userRepository.save(user);
        }
        return issueSession(user);
    }

    @Transactional
    public Map<String, Object> register(String emailId, String password, String firstName, String lastName) {
        String email = emailId == null ? "" : emailId.trim().toLowerCase();
        String raw = password == null ? "" : password;
        if (email.isBlank() || raw.isBlank()) {
            throw new AuthException("Email and password are required.", "AUTH_VALIDATION_FAILED", 400);
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new AuthException("Please enter a valid email address.", "AUTH_VALIDATION_FAILED", 400);
        }
        String policy = passwordPolicy(raw);
        if (policy != null) {
            throw new AuthException(policy, "AUTH_PASSWORD_POLICY", 400);
        }
        if (userRepository.findActiveByEmail(email).isPresent()) {
            throw new AuthException("An account with this email already exists.", "AUTH_ACCOUNT_EXISTS", 409);
        }
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(raw));
        user.setFirstName(firstName == null ? "" : firstName.trim());
        user.setLastName(lastName == null ? "" : lastName.trim());
        user.setAuthProvider("password");
        user.setRole("user");
        user = userRepository.save(user);
        Map<String, Object> out = new HashMap<>();
        out.put("userId", user.getId());
        out.put("email", user.getEmail());
        out.put("firstName", nullToEmpty(user.getFirstName()));
        out.put("lastName", nullToEmpty(user.getLastName()));
        out.put("role", user.getRole());
        return out;
    }

    @Transactional
    public Map<String, Object> refreshSession(String refreshTokenValue) {
        String supplied = refreshTokenValue == null ? "" : refreshTokenValue.trim();
        if (supplied.isBlank()) {
            throw new AuthException("Refresh token is required.", "AUTH_REFRESH_INVALID", 401);
        }
        RefreshTokenEntity stored = refreshTokenRepository.findActiveByToken(supplied)
                .orElseThrow(() -> new AuthException("Refresh token is invalid.", "AUTH_REFRESH_INVALID", 401));
        if (!stored.getExpiry().isAfter(Instant.now())) {
            stored.setDeletedAt(Instant.now());
            refreshTokenRepository.save(stored);
            throw new AuthException("Refresh token has expired.", "AUTH_REFRESH_INVALID", 401);
        }
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(supplied);
        } catch (Exception e) {
            stored.setDeletedAt(Instant.now());
            refreshTokenRepository.save(stored);
            throw new AuthException("Refresh token is invalid.", "AUTH_REFRESH_INVALID", 401);
        }
        if (!"refresh".equalsIgnoreCase(String.valueOf(claims.get("tokenType")))) {
            throw new AuthException("Refresh token is invalid.", "AUTH_REFRESH_INVALID", 401);
        }
        UserEntity user = userRepository.findActiveById(stored.getUserId())
                .orElseThrow(() -> new AuthException("Refresh token is invalid.", "AUTH_REFRESH_INVALID", 401));
        if (!user.getId().toString().equals(claims.getSubject())
                || ((Number) claims.get("tokenVersion")).longValue() != user.getTokenVersion()) {
            throw new AuthException("Refresh token is invalid.", "AUTH_REFRESH_INVALID", 401);
        }
        stored.setDeletedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return issueSession(user);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        String supplied = refreshTokenValue == null ? "" : refreshTokenValue.trim();
        if (supplied.isBlank()) return;
        refreshTokenRepository.findActiveByToken(supplied).ifPresent(t -> {
            t.setDeletedAt(Instant.now());
            refreshTokenRepository.save(t);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUser(String accessTokenValue) {
        String token = accessTokenValue == null ? "" : accessTokenValue.trim();
        if (token.isBlank()) {
            throw new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401);
        }
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (Exception e) {
            throw new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401);
        }
        if (!"access".equalsIgnoreCase(String.valueOf(claims.get("tokenType")))) {
            throw new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401);
        }
        UUID userId = UUID.fromString(claims.getSubject());
        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401));
        if (((Number) claims.get("tokenVersion")).longValue() != user.getTokenVersion()) {
            throw new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401);
        }
        return toPublicUser(user);
    }

    @Transactional(readOnly = true)
    public UserEntity requireActiveUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new AuthException("Not authenticated.", "AUTH_UNAUTHORIZED", 401));
    }

    private Map<String, Object> issueSession(UserEntity user) {
        if (user == null || !user.isActive()) {
            throw new AuthException("Your account is unavailable. Please contact support if this persists.",
                    "AUTH_ACCOUNT_UNAVAILABLE", 403);
        }
        String access = jwtService.generateAccessToken(user.getId(), user.getRole(), user.getTokenVersion());
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getTokenVersion());
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setToken(refresh);
        entity.setUserId(user.getId());
        entity.setExpiry(Instant.now().plusSeconds(jwtService.getRefreshSeconds()));
        entity.setDeviceId("browser");
        refreshTokenRepository.save(entity);

        Map<String, Object> session = new HashMap<>(toPublicUser(user));
        session.put("accessToken", access);
        session.put("refreshToken", refresh);
        session.put("expiresInSeconds", jwtService.getAccessSeconds());
        session.put("refreshExpiresInSeconds", jwtService.getRefreshSeconds());
        return session;
    }

    public static Map<String, Object> publicSession(Map<String, Object> session) {
        Map<String, Object> copy = new HashMap<>(session);
        copy.remove("accessToken");
        copy.remove("refreshToken");
        return copy;
    }

    private Map<String, Object> toPublicUser(UserEntity user) {
        Map<String, Object> out = new HashMap<>();
        out.put("userId", user.getId());
        out.put("email", user.getEmail());
        out.put("firstName", nullToEmpty(user.getFirstName()));
        out.put("lastName", nullToEmpty(user.getLastName()));
        out.put("username", displayName(user));
        out.put("role", user.getRole() == null ? "user" : user.getRole());
        out.put("profilePic", nullToEmpty(user.getProfilePic()));
        return out;
    }

    private static String displayName(UserEntity user) {
        String full = (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).trim();
        if (!full.isBlank()) return full;
        String email = user.getEmail() == null ? "" : user.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : "User";
    }

    private static String passwordPolicy(String password) {
        if (password.length() < 8) return "Password must be at least 8 characters.";
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) return "Password must include at least one letter and one number.";
        return null;
    }

    private static String nullToEmpty(String v) { return v == null ? "" : v; }
}
