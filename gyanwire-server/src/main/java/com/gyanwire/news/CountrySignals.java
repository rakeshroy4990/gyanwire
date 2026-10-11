package com.gyanwire.news;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Country of a finding, from the headline first and the source region after that.
 * India wins when the text names India and another country.
 */
public final class CountrySignals {

    public record Country(String code, String name, Pattern pattern) {
    }

    private static final List<Country> COUNTRIES = List.of(
            country("IN", "India", "india|indian|delhi|mumbai|bengaluru|bangalore|hyderabad|chennai|kolkata|pune|sebi|rbi|nse|bse|cdsco|isro|meity|in-space"),
            country("US", "United States", "united states|u\\.s\\.a\\.|u\\.s\\.|usa|fda|nasdaq|silicon valley|wall street|white house|california|new york"),
            country("GB", "United Kingdom", "united kingdom|britain|british|london|nhs|uk"),
            country("CN", "China", "china|chinese|beijing|shanghai"),
            country("JP", "Japan", "japan|japanese|tokyo"),
            country("DE", "Germany", "germany|german|berlin"),
            country("FR", "France", "france|french|paris"),
            country("SG", "Singapore", "singapore"),
            country("AE", "United Arab Emirates", "united arab emirates|uae|dubai|abu dhabi"),
            country("AU", "Australia", "australia|australian|sydney"),
            country("CA", "Canada", "canada|canadian|toronto"),
            country("KR", "South Korea", "south korea|seoul|korean"),
            country("BR", "Brazil", "brazil|brazilian"),
            country("NL", "Netherlands", "netherlands|dutch|amsterdam"),
            country("SE", "Sweden", "sweden|swedish|stockholm"),
            country("CH", "Switzerland", "switzerland|swiss|zurich"),
            country("IL", "Israel", "israel|israeli"),
            country("ZA", "South Africa", "south africa"),
            country("ID", "Indonesia", "indonesia|indonesian|jakarta"),
            country("NG", "Nigeria", "nigeria|nigerian|lagos"),
            country("KE", "Kenya", "kenya|kenyan|nairobi"),
            country("SA", "Saudi Arabia", "saudi arabia|saudi"),
            country("HK", "Hong Kong", "hong kong"),
            country("TW", "Taiwan", "taiwan"),
            country("NZ", "New Zealand", "new zealand")
    );

    private static final Map<String, String> NAMES = names();

    private CountrySignals() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!NAMES.containsKey(code)) {
            throw new IllegalArgumentException(code);
        }
        return code;
    }

    public static String name(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return NAMES.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public static String detect(String text, String sourceRegion, String domain) {
        String blob = text == null ? "" : text;
        if (matches("IN", blob)) {
            return "IN";
        }
        String fromText = earliest(blob);
        if (fromText != null) {
            return fromText;
        }
        String fromDomain = fromDomain(domain);
        if (fromDomain != null) {
            return fromDomain;
        }
        if (sourceRegion == null || sourceRegion.isBlank()) {
            return null;
        }
        String region = sourceRegion.trim().toUpperCase(Locale.ROOT);
        return NAMES.containsKey(region) ? region : null;
    }

    public static void assignMissing(List<NewsRow> rows) {
        if (rows == null) {
            return;
        }
        for (NewsRow row : rows) {
            if (row == null || (row.country() != null && !row.country().isBlank())) {
                continue;
            }
            String text = ((row.title() == null ? "" : row.title()) + " " + (row.summary() == null ? "" : row.summary())).trim();
            row.country(detect(text, row.sourceRegion(), row.domain()));
        }
    }

    public static List<NewsRow> matching(List<NewsRow> rows, String code) {
        if (rows == null) {
            return List.of();
        }
        if (code == null || code.isBlank()) {
            return rows;
        }
        List<NewsRow> out = new ArrayList<>();
        for (NewsRow row : rows) {
            if (row != null && code.equalsIgnoreCase(row.country())) {
                out.add(row);
            }
        }
        return out;
    }

    public static List<Map<String, Object>> options(List<NewsRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (rows != null) {
            for (NewsRow row : rows) {
                if (row == null || row.country() == null || row.country().isBlank()) {
                    continue;
                }
                String code = row.country().trim().toUpperCase(Locale.ROOT);
                if (NAMES.containsKey(code)) {
                    counts.merge(code, 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Integer india = counts.remove("IN");
        if (india != null) {
            out.add(option("IN", india));
        }
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> out.add(option(entry.getKey(), entry.getValue())));
        return out;
    }

    public static void ensureOption(List<Map<String, Object>> options, String code) {
        if (code == null || code.isBlank() || options == null) {
            return;
        }
        for (Map<String, Object> option : options) {
            if (code.equals(option.get("code"))) {
                return;
            }
        }
        options.add(option(code, 0));
    }

    private static Map<String, Object> option(String code, int count) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("code", code);
        option.put("name", NAMES.get(code));
        option.put("count", count);
        return option;
    }

    private static boolean matches(String code, String text) {
        for (Country country : COUNTRIES) {
            if (country.code().equals(code)) {
                return country.pattern().matcher(text).find();
            }
        }
        return false;
    }

    private static String earliest(String text) {
        String best = null;
        int at = Integer.MAX_VALUE;
        for (Country country : COUNTRIES) {
            if ("IN".equals(country.code())) {
                continue;
            }
            Matcher matcher = country.pattern().matcher(text);
            if (matcher.find() && matcher.start() < at) {
                at = matcher.start();
                best = country.code();
            }
        }
        return best;
    }

    private static String fromDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String host = domain.toLowerCase(Locale.ROOT).trim();
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        if (host.endsWith(".co.uk") || host.endsWith(".uk")) {
            return "GB";
        }
        if (host.endsWith(".co.in") || host.endsWith(".in")) {
            return "IN";
        }
        if (host.endsWith(".com.au") || host.endsWith(".au")) {
            return "AU";
        }
        if (host.endsWith(".co.jp") || host.endsWith(".jp")) {
            return "JP";
        }
        if (host.endsWith(".de")) {
            return "DE";
        }
        if (host.endsWith(".fr")) {
            return "FR";
        }
        if (host.endsWith(".ca")) {
            return "CA";
        }
        if (host.endsWith(".sg")) {
            return "SG";
        }
        if (host.endsWith(".cn")) {
            return "CN";
        }
        if (host.endsWith(".kr")) {
            return "KR";
        }
        if (host.endsWith(".br")) {
            return "BR";
        }
        if (host.endsWith(".ae")) {
            return "AE";
        }
        if (host.endsWith(".nz")) {
            return "NZ";
        }
        if (host.endsWith(".za")) {
            return "ZA";
        }
        if (host.endsWith(".hk")) {
            return "HK";
        }
        if (host.endsWith(".tw")) {
            return "TW";
        }
        if (host.endsWith(".nl")) {
            return "NL";
        }
        if (host.endsWith(".se")) {
            return "SE";
        }
        if (host.endsWith(".ch")) {
            return "CH";
        }
        if (host.endsWith(".il")) {
            return "IL";
        }
        if (host.endsWith(".id")) {
            return "ID";
        }
        if (host.endsWith(".ng")) {
            return "NG";
        }
        if (host.endsWith(".ke")) {
            return "KE";
        }
        if (host.endsWith(".sa")) {
            return "SA";
        }
        return null;
    }

    private static Country country(String code, String name, String body) {
        return new Country(code, name, Pattern.compile(
                "(?<![A-Za-z0-9])(?:" + body + ")(?![A-Za-z0-9])",
                Pattern.CASE_INSENSITIVE));
    }

    private static Map<String, String> names() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Country country : COUNTRIES) {
            names.put(country.code(), country.name());
        }
        return Map.copyOf(names);
    }
}
