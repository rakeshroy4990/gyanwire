package com.gyanwire.news;

import java.util.UUID;

/** Signed-in user or anonymous IP hash for personalized news state. */
public record NewsViewer(UUID userId, String anonHash) {

    public static NewsViewer of(UUID userId, String anonHash) {
        if (userId != null) {
            return new NewsViewer(userId, null);
        }
        String hash = anonHash == null || anonHash.isBlank() ? null : anonHash;
        return new NewsViewer(null, hash);
    }

    public String cacheKey() {
        if (userId != null) {
            return "u:" + userId;
        }
        if (anonHash != null) {
            return "a:" + anonHash;
        }
        return "anon";
    }
}
