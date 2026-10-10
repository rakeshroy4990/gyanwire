package com.gyanwire.news;

public final class SeedPolicy {

    private SeedPolicy() {
    }

    public static boolean shouldSeed(boolean prodProfile, boolean seedAllowed, boolean hasRealRows, boolean fetchFailed, boolean seedOnEmpty) {
        if (prodProfile || !seedAllowed || hasRealRows) {
            return false;
        }
        return fetchFailed || seedOnEmpty;
    }
}
