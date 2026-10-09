package com.gyanwire.plans;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OutlineDraft {

    private OutlineDraft() {
    }

    public static Map<String, Object> template(String title, int monthlyCost, int score) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("problem", "People affected by this news need a simpler way to act. Source: idea.");
        content.put("customer", "The first customers match the profile skills and location. Source: profile.");
        content.put("offer", title == null ? "A small paid service" : title);
        content.put("pricing", "Estimate. Source: plan JSON. Start with a pilot price.");
        content.put("channels", "Direct conversations, then a short public page.");
        content.put("monthlyCost", monthlyCost);
        content.put("monthlyCostSource", "plan");
        content.put("breakEvenCustomers", monthlyCost <= 0 ? 0 : Math.max(1, monthlyCost / 500));
        content.put("milestones90", "Days 1-30 learn, 31-60 proof, 61-90 first commitment.");
        content.put("risks", "Regulatory and demand risk. Score " + score + " is a model of fit, not a forecast.");
        content.put("skills", "Skills still to build are the unpaid gaps in the skill plan.");
        return content;
    }

    public static boolean hasNineSections(Map<String, Object> content) {
        return content != null && content.keySet().containsAll(java.util.List.of(
                "problem", "customer", "offer", "pricing", "channels",
                "monthlyCost", "breakEvenCustomers", "milestones90", "risks", "skills"
        ));
    }
}
