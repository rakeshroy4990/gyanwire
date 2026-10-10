package com.gyanwire.plans;

import java.util.List;
import java.util.Locale;

/**
 * Maps planner phase + tool skills to What-If task types.
 */
public final class WeekTaskType {

    public static final int HOURS_PER_SITTING = 5;

    private WeekTaskType() {
    }

    public static String resolve(String phase, List<String> skills) {
        String p = phase == null ? "" : phase.toLowerCase(Locale.ROOT);
        if ("prove".equals(p)) {
            return "outreach";
        }
        if ("decide".equals(p)) {
            return "learning";
        }
        if ("learn".equals(p)) {
            String fromSkills = fromSkills(skills);
            if ("writing".equals(fromSkills) || "research".equals(fromSkills)) {
                return fromSkills;
            }
            return "learning";
        }
        // build — default to writing/notes work, not an IDE, when the tool has no skill signal
        String fromSkills = fromSkills(skills);
        return fromSkills == null ? "writing" : fromSkills;
    }

    private static String fromSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        for (String raw : skills) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String s = raw.toLowerCase(Locale.ROOT);
            if (s.contains("design") || s.equals("figma")) {
                return "design";
            }
            if (s.contains("writing") || s.equals("notes")) {
                return "writing";
            }
            if (s.contains("sheet") || s.contains("ops") || s.contains("analytics") || s.equals("data")) {
                return "data";
            }
            if (s.contains("python") || s.equals("ide") || s.equals("code") || s.contains("program")) {
                return "coding";
            }
            if (s.contains("research") || s.contains("reading")) {
                return "research";
            }
            if (s.contains("sales") || s.contains("network") || s.contains("outreach")) {
                return "outreach";
            }
            if (s.contains("learn")) {
                return "learning";
            }
        }
        return null;
    }

    public static int sittings(int hoursPerWeek) {
        return Math.max(1, hoursPerWeek / HOURS_PER_SITTING);
    }

    public static double baseHours(int hoursPerWeek) {
        return sittings(hoursPerWeek) * (double) HOURS_PER_SITTING;
    }
}
