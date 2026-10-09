package com.gyanwire.digests;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class DigestDeduper {

    private DigestDeduper() {
    }

    public static String urlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(url).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(String.valueOf(url).hashCode());
        }
    }

    public static List<String> unseen(Set<String> already, List<String> urls) {
        List<String> fresh = new ArrayList<>();
        if (urls == null) {
            return fresh;
        }
        for (String url : urls) {
            String hash = urlHash(url);
            if (already == null || !already.contains(hash)) {
                fresh.add(url);
            }
        }
        return fresh;
    }
}
