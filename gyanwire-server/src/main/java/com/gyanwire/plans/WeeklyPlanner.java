package com.gyanwire.plans;

import java.util.ArrayList;
import java.util.List;

public final class WeeklyPlanner {

    public record Week(int weekNo, String outcome, List<String> tasks, String metric) {
    }

    private WeeklyPlanner() {
    }

    public static int horizon(int hoursPerWeek) {
        int weeks = hoursPerWeek < 5 ? 8 : 12;
        return Math.min(16, Math.max(1, weeks));
    }

    public static List<Week> build(int hoursPerWeek, boolean lighter) {
        int weeks = horizon(hoursPerWeek);
        int taskCount = lighter ? 2 : 3;
        List<Week> out = new ArrayList<>();
        for (int i = 1; i <= weeks; i++) {
            List<String> tasks = new ArrayList<>();
            tasks.add("Practice the core skill for " + Math.max(1, hoursPerWeek / 5) + " focused sittings");
            tasks.add(lighter ? "Review notes from the previous week" : "Ship one small proof artifact");
            if (!lighter) {
                tasks.add(i >= weeks - 1 ? "Decide continue or pivot using the outline" : "Talk to one potential customer");
            }
            out.add(new Week(i, outcome(i, weeks), tasks.subList(0, Math.min(taskCount, tasks.size())), metric(i)));
        }
        return out;
    }

    public static boolean shouldLighten(int missedStreak) {
        return missedStreak >= 2;
    }

    private static String outcome(int week, int total) {
        if (week == 1) {
            return "Baseline and goal locked";
        }
        if (week == total) {
            return "Review and next decision";
        }
        if (week < total / 2) {
            return "Core skill foundation";
        }
        return "Proof and validation";
    }

    private static String metric(int week) {
        return week == 1 ? "Goal written" : "Hours done";
    }
}
