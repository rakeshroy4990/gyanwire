package com.gyanwire.news;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class NewsPackRegistry {

    private final Map<String, Pack> byIndustry = new LinkedHashMap<>();

    public NewsPackRegistry() {
        load();
    }

    public Optional<Pack> pack(String industry) {
        if (industry == null || industry.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIndustry.get(industry.trim()));
    }

    public Map<String, Pack> all() {
        return Map.copyOf(byIndustry);
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:news/packs/*.yml");
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    Object raw = yaml.load(in);
                    if (!(raw instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Map<String, Object> row = (Map<String, Object>) map;
                    String industry = str(row.get("industry"));
                    if (industry.isBlank()) {
                        continue;
                    }
                    List<String> domains = stringList(row.get("allow_domains"));
                    List<String> requireKeywords = stringList(row.get("require_keywords"));
                    boolean requireSignalMatch = bool(row.get("require_signal_match"));
                    Map<String, Object> deny = row.get("deny") instanceof Map<?, ?> d
                            ? (Map<String, Object>) d
                            : Map.of();
                    List<String> denyDomains = stringList(deny.get("domains"));
                    List<String> denyKeywords = stringList(deny.get("keywords"));
                    Map<String, List<String>> signals = new LinkedHashMap<>();
                    if (row.get("signals") instanceof Map<?, ?> sig) {
                        for (Map.Entry<?, ?> e : sig.entrySet()) {
                            signals.put(String.valueOf(e.getKey()), stringList(e.getValue()));
                        }
                    }
                    byIndustry.put(industry, new Pack(
                            industry,
                            domains,
                            requireKeywords,
                            requireSignalMatch,
                            denyDomains,
                            denyKeywords,
                            signals));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load news packs", ex);
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String s = str(item);
                if (!s.isBlank()) {
                    out.add(s.toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Pack(
            String industry,
            List<String> allowDomains,
            List<String> requireKeywords,
            boolean requireSignalMatch,
            List<String> denyDomains,
            List<String> denyKeywords,
            Map<String, List<String>> signals
    ) {
        public boolean domainAllowed(String domain) {
            if (domain == null || domain.isBlank()) {
                return false;
            }
            if (allowDomains == null || allowDomains.isEmpty()) {
                return true;
            }
            String d = domain.toLowerCase(Locale.ROOT);
            for (String allow : allowDomains) {
                if (d.equals(allow) || d.endsWith("." + allow)) {
                    return true;
                }
            }
            return false;
        }

        public boolean domainDenied(String domain) {
            if (domain == null || denyDomains == null) {
                return false;
            }
            String d = domain.toLowerCase(Locale.ROOT);
            for (String deny : denyDomains) {
                if (d.equals(deny) || d.endsWith("." + deny)) {
                    return true;
                }
            }
            return false;
        }

        /** When set, title/summary must mention at least one industry term. */
        public boolean requireMatched(String text) {
            if (requireKeywords == null || requireKeywords.isEmpty()) {
                return true;
            }
            if (text == null || text.isBlank()) {
                return false;
            }
            String blob = text.toLowerCase(Locale.ROOT);
            for (String kw : requireKeywords) {
                if (!kw.isBlank() && termMatches(blob, kw)) {
                    return true;
                }
            }
            return false;
        }

        public boolean keywordDenied(String text) {
            if (text == null || denyKeywords == null || denyKeywords.isEmpty()) {
                return false;
            }
            String blob = text.toLowerCase(Locale.ROOT);
            for (String kw : denyKeywords) {
                if (!kw.isBlank() && termMatches(blob, kw)) {
                    return true;
                }
            }
            return false;
        }

        /** Pack signal keywords present in text (ignores stored classifier signalType). */
        public List<String> matchedSignals(String text) {
            List<String> hit = new ArrayList<>();
            if (signals == null || signals.isEmpty() || text == null || text.isBlank()) {
                return hit;
            }
            String blob = text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> e : signals.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                for (String term : e.getValue()) {
                    if (term != null && !term.isBlank() && termMatches(blob, term.toLowerCase(Locale.ROOT))) {
                        hit.add(e.getKey());
                        break;
                    }
                }
            }
            return hit;
        }

        /**
         * Multi-word phrases use substring match; single tokens use word boundaries
         * so "ai" does not hit "email" / "paid" / "train".
         */
        static boolean termMatches(String blob, String term) {
            String t = term.toLowerCase(Locale.ROOT).trim();
            if (t.isEmpty() || blob == null || blob.isEmpty()) {
                return false;
            }
            if (t.indexOf(' ') >= 0) {
                return blob.contains(t);
            }
            return Pattern.compile("\\b" + Pattern.quote(t) + "\\b").matcher(blob).find();
        }
    }
}
