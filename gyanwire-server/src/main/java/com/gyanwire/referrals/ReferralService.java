package com.gyanwire.referrals;

import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.ResearchException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ReferralService {

    private final FlowStore store;

    public ReferralService(FlowStore store) {
        this.store = store;
    }

    public Map<String, Object> mine(UUID userId) {
        String code = store.ensureReferralCode(userId);
        return Map.of("code", code, "bonusSearches", store.bonusSearches(userId));
    }

    public Map<String, Object> redeem(UUID userId, String code) {
        String normalized = ReferralCodes.normalize(code);
        if (normalized.isBlank()) {
            throw new ResearchException("Send a referral code.", "VALIDATION_ERROR", 400);
        }
        String own = store.ensureReferralCode(userId);
        if (own.equalsIgnoreCase(normalized)) {
            throw new ResearchException("You cannot redeem your own code.", "VALIDATION_ERROR", 400);
        }
        // Reject predictable pre-V42 UUID-prefix codes even if someone still types them.
        if (ReferralCodes.matchesLegacy(userId, normalized)) {
            throw new ResearchException("That code was not found.", "VALIDATION_ERROR", 400);
        }
        UUID referrer = store.referrerForCode(normalized);
        if (referrer == null) {
            throw new ResearchException("That code was not found.", "VALIDATION_ERROR", 400);
        }
        if (referrer.equals(userId)) {
            throw new ResearchException("You cannot redeem your own code.", "VALIDATION_ERROR", 400);
        }
        int bonus = ReferralCodes.bonusFor(store.alreadyRedeemed(userId) ? 1 : 0, store.referralCount(referrer));
        if (bonus == 0) {
            throw new ResearchException("That code cannot add more searches.", "VALIDATION_ERROR", 400);
        }
        store.redeem(referrer, userId, normalized, bonus);
        return Map.of("bonusSearches", bonus);
    }
}
