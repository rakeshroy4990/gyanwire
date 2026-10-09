package com.gyanwire.referrals;

import java.util.UUID;

public final class ReferralCodes {

    public static final int BONUS_SEARCHES = 2;
    public static final int ABUSE_CAP = 20;

    private ReferralCodes() {
    }

    public static String codeFor(UUID userId) {
        String raw = userId.toString().replace("-", "");
        return raw.substring(0, 8);
    }

    public static int bonusFor(int redeemedByThisUser, int grantedToReferrer) {
        if (redeemedByThisUser > 0) {
            return 0;
        }
        if (grantedToReferrer >= ABUSE_CAP) {
            return 0;
        }
        return BONUS_SEARCHES;
    }
}
