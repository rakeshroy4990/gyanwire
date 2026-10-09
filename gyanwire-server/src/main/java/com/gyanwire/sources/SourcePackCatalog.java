package com.gyanwire.sources;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SourcePackCatalog {

    private final AtomicReference<Map<String, Double>> weights = new AtomicReference<>(Map.of());
    private final ThreadLocal<Map<String, Double>> requestWeights = new ThreadLocal<>();

    public void replace(Map<String, Double> next) {
        weights.set(next == null ? Map.of() : Map.copyOf(next));
    }

    public void use(Map<String, Double> next) {
        requestWeights.set(next == null ? Map.of() : next);
    }

    public void clear() {
        requestWeights.remove();
    }

    public double weight(String host) {
        if (host == null || host.isBlank()) {
            return 1;
        }
        String h = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        Map<String, Double> active = requestWeights.get() != null ? requestWeights.get() : weights.get();
        double found = 1;
        for (Map.Entry<String, Double> entry : active.entrySet()) {
            String domain = entry.getKey().toLowerCase(Locale.ROOT);
            if (h.equals(domain) || h.endsWith("." + domain)) {
                found = entry.getValue();
            }
        }
        return found;
    }
}
