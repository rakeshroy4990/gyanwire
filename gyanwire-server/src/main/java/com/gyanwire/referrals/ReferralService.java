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
        return Map.of("code", ReferralCodes.codeFor(userId), "bonusSearches", store.bonusSearches(userId));
    }

    public Map<String, Object> redeem(UUID userId, String code) {
        if (ReferralCodes.codeFor(userId).equals(code)) {
            throw new ResearchException("You cannot redeem your own code.", "VALIDATION_ERROR", 400);
        }
        UUID referrer = store.referrerForCode(code);
        if (referrer == null) {
            throw new ResearchException("That code was not found.", "VALIDATION_ERROR", 400);
        }
        int bonus = ReferralCodes.bonusFor(store.alreadyRedeemed(userId) ? 1 : 0, store.referralCount(referrer));
        if (bonus == 0) {
            throw new ResearchException("That code cannot add more searches.", "VALIDATION_ERROR", 400);
        }
        store.redeem(referrer, userId, code, bonus);
        return Map.of("bonusSearches", bonus);
    }
}
