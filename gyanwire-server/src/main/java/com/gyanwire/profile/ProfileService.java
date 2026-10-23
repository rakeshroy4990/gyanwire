package com.gyanwire.profile;

import com.gyanwire.auth.AuthService;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.ResearchException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileService {

    private final FlowStore store;
    private final AuthService authService;

    public ProfileService(FlowStore store, AuthService authService) {
        this.store = store;
        this.authService = authService;
    }

    public Map<String, Object> get(UUID userId) {
        Map<String, Object> profile = store.findProfile(userId);
        if (profile != null) {
            return profile;
        }
        Map<String, Object> empty = new HashMap<>();
        empty.put("consentAt", null);
        return empty;
    }

    public Map<String, Object> save(UUID userId, Map<String, Object> body) {
        if (!Boolean.TRUE.equals(body.get("consent"))) {
            throw new ResearchException("Consent is required before we store a profile.", "CONSENT_REQUIRED", 400);
        }
        store.saveProfile(userId, body);
        return get(userId);
    }

    public void delete(UUID userId) {
        store.deleteProfile(userId);
    }

    /**
     * Requires the account password (or typed DELETE for passwordless Google accounts)
     * so a single click or CSRF cannot wipe the profile.
     */
    public void deleteWithPassword(UUID userId, String password) {
        deleteWithPassword(userId, password, null);
    }

    public void deleteWithPassword(UUID userId, String password, String confirm) {
        if (!authService.hasPassword(userId)) {
            if (!"DELETE".equals(confirm == null ? "" : confirm.trim())) {
                throw new ResearchException(
                        "Type DELETE to confirm profile deletion.",
                        "CONFIRM_REQUIRED",
                        400);
            }
            store.deleteProfile(userId);
            return;
        }
        if (password == null || password.isBlank()) {
            throw new ResearchException(
                    "Enter your password to delete profile data.",
                    "CONFIRM_REQUIRED",
                    400);
        }
        authService.requirePassword(userId, password);
        store.deleteProfile(userId);
    }

    public void requireConsent(UUID userId) {
        Map<String, Object> profile = store.findProfile(userId);
        if (profile == null || profile.get("consentAt") == null) {
            throw new ResearchException("Add a profile and consent before generating ideas.", "CONSENT_REQUIRED", 403);
        }
    }

    /**
     * First idea click stores a starter profile with consent so the card can open
     * immediately. Users refine under "Refine more" afterward.
     */
    public Map<String, Object> ensureStarter(UUID userId, String industry) {
        Map<String, Object> profile = store.findProfile(userId);
        if (profile != null && profile.get("consentAt") != null) {
            return profile;
        }
        Map<String, Object> starter = new HashMap<>();
        starter.put("consent", true);
        starter.put("persona", "working");
        starter.put("goal90d", "learn");
        starter.put("capitalBand", "0");
        starter.put("incomeBand", "0");
        starter.put("investPct", 10);
        starter.put("hoursPerWeek", 10);
        starter.put("languages", java.util.List.of("en"));
        starter.put("industries", java.util.List.of(industry == null || industry.isBlank() ? "IT" : industry));
        starter.put("assets", java.util.List.of());
        starter.put("constraints", java.util.List.of());
        starter.put("riskAppetite", "low");
        store.saveProfile(userId, starter);
        return get(userId);
    }

    public Map<String, Object> requireProfile(UUID userId) {
        return ensureStarter(userId, "IT");
    }
}
