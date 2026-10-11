package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public enum AngleType {
    comparison_content,
    buying_guide_or_alert,
    import_or_reseller,
    accessories_or_addons,
    b2b_service,
    data_or_tool,
    community_or_newsletter,
    education_or_training;

    /** Display order: social content first, education second, then everything else. */
    private static final List<String> PREFERRED = List.of(
            comparison_content.name(),
            education_or_training.name());

    private static final Pattern BUSINESS = Pattern.compile(
            "\\b(clinics?|hospitals?|labs?|gyms?|retailers?|sellers?|shops?|stores?|distributors?|"
                    + "brands?|companies|businesses|employers?|startups?|manufacturers?|pharmacies|"
                    + "insurers?|banks?|agencies|resellers?|importers?)\\b",
            Pattern.CASE_INSENSITIVE);

    public String label() {
        return switch (this) {
            case comparison_content -> "Social media";
            case buying_guide_or_alert -> "Buying guide or alert";
            case import_or_reseller -> "Import or reseller";
            case accessories_or_addons -> "Accessories or add-ons";
            case b2b_service -> "B2B service";
            case data_or_tool -> "Data or tool";
            case community_or_newsletter -> "Community or newsletter";
            case education_or_training -> "Education or training";
        };
    }

    /**
     * Lower is better for card order. Social content = 0, education = 1, other = 2.
     */
    public static int displayPriority(String angle) {
        if (comparison_content.name().equals(angle)) {
            return 0;
        }
        if (education_or_training.name().equals(angle)) {
            return 1;
        }
        return 2;
    }

    public static boolean isPreferred(String angle) {
        return PREFERRED.contains(angle);
    }

    /**
     * Puts social content first and education second when allowed, and forces those
     * two in when the model omitted them. Caps at 5.
     */
    public static List<String> prioritize(List<String> picked, List<String> allowed) {
        Set<String> permit = allowed == null ? Set.of() : new LinkedHashSet<>(allowed);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String preferred : PREFERRED) {
            if (permit.contains(preferred)) {
                ordered.add(preferred);
            }
        }
        if (picked != null) {
            for (String angle : picked) {
                if (permit.contains(angle)) {
                    ordered.add(angle);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String angle : ordered) {
            if (out.size() >= 5) {
                break;
            }
            out.add(angle);
        }
        return List.copyOf(out);
    }

    public static boolean known(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            valueOf(value.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static boolean hasBusinessGroup(List<String> groups) {
        if (groups == null) {
            return false;
        }
        for (String group : groups) {
            if (group != null && BUSINESS.matcher(group).find()) {
                return true;
            }
        }
        return false;
    }

    public static List<String> allowed(List<String> groups) {
        List<String> out = new ArrayList<>();
        boolean business = hasBusinessGroup(groups);
        for (AngleType type : values()) {
            if (type == b2b_service && !business) {
                continue;
            }
            out.add(type.name());
        }
        return List.copyOf(out);
    }

    public static String labelOf(String value) {
        if (!known(value)) {
            return "Idea";
        }
        return valueOf(value.trim().toLowerCase(Locale.ROOT)).label();
    }
}
