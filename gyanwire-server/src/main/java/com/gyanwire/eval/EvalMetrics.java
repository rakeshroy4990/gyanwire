package com.gyanwire.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EvalMetrics {

    private EvalMetrics() {
    }

    public static double precisionAt(List<String> ranked, Set<String> good, int k) {
        if (ranked == null || ranked.isEmpty() || good == null || good.isEmpty() || k <= 0) {
            return 0;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (good.contains(ranked.get(i))) {
                hits++;
            }
        }
        return hits / (double) k;
    }

    public static double ndcgAt(List<String> ranked, Set<String> good, int k) {
        if (ranked == null || good == null || good.isEmpty() || k <= 0) {
            return 0;
        }
        double dcg = 0;
        int limit = Math.min(k, ranked == null ? 0 : ranked.size());
        for (int i = 0; i < limit; i++) {
            if (good.contains(ranked.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        int idealHits = Math.min(k, good.size());
        double idcg = 0;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        if (idcg == 0) {
            return 0;
        }
        return dcg / idcg;
    }

    public static double trustedShare(List<String> ranked, int k) {
        if (ranked == null || ranked.isEmpty() || k <= 0) {
            return 0;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (isTrusted(ranked.get(i))) {
                hits++;
            }
        }
        return hits / (double) limit;
    }

    public static boolean isTrusted(String url) {
        if (url == null) {
            return false;
        }
        String host = url.toLowerCase();
        return host.contains("arxiv.org")
                || host.contains("gov")
                || host.contains("wikipedia.org")
                || host.contains("nih.gov")
                || host.contains("who.int")
                || host.contains("isro")
                || host.contains("nasa")
                || host.contains("edu")
                || host.contains("github.com")
                || host.contains("sebi.gov.in")
                || host.contains("rbi.org.in");
    }

    public static Set<String> setOf(List<String> urls) {
        return new HashSet<>(urls);
    }
}
