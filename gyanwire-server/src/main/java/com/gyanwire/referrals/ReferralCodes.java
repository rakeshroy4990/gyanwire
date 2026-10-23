package com.gyanwire.referrals;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public final class ReferralCodes {

    public static final int BONUS_SEARCHES = 2;
    public static final int ABUSE_CAP = 20;
    public static final int CODE_LENGTH = 10;

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferralCodes() {
    }

    /** Opaque random code — never derive from userId. */
    public static String generate() {
        char[] out = new char[CODE_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Legacy codes were the first 8 hex chars of the user UUID (no dashes).
     * Kept only so already-shared links still resolve once.
     */
    public static boolean matchesLegacy(UUID userId, String code) {
        if (userId == null || code == null || code.length() != 8) {
            return false;
        }
        String raw = userId.toString().replace("-", "");
        return raw.substring(0, 8).equalsIgnoreCase(code);
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
