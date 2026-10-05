package com.gyanwire.research.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class IndiaSupport {
    private IndiaSupport() {}

    public static final List<String> INDIA_DOMAIN_HINTS = List.of(
            "isro.gov.in", "nic.in", "gov.in", "ac.in", "res.in",
            "moneycontrol.com", "economictimes.indiatimes.com", "livemint.com",
            "thehindu.com", "indianexpress.com", ".in"
    );

    public static List<String> indiaFirstQueries(String query) {
        String q = query == null ? "" : query.trim();
        Set<String> out = new LinkedHashSet<>();
        if (!q.toLowerCase(Locale.ROOT).contains("india")) {
            out.add(q + " India");
        }
        out.add(q);
        return new ArrayList<>(out);
    }

    public static int indiaAffinity(String host) {
        if (host == null) return 0;
        String h = host.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String hint : INDIA_DOMAIN_HINTS) {
            if (h.contains(hint) || h.endsWith(hint)) score += 8;
        }
        return score;
    }

    public static <T> List<T> sortIndiaFirst(List<T> items, java.util.function.Function<T, String> hostFn) {
        List<T> copy = new ArrayList<>(items);
        copy.sort((a, b) -> Integer.compare(indiaAffinity(hostFn.apply(b)), indiaAffinity(hostFn.apply(a))));
        return copy;
    }
}
