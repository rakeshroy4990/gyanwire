package com.gyanwire.llm;

import java.util.Set;

/**
 * Feature name to model id. High-thinking calls are idea generation and the
 * business outline. Everything else stays on the low model.
 */
public final class ModelRouter {

    public static final Set<String> HIGH_FEATURES = Set.of("idea", "outline");

    private final String lowModel;
    private final String highModel;

    public ModelRouter(String lowModel, String highModel) {
        this.lowModel = blank(lowModel) ? "gpt-4o-mini" : lowModel.trim();
        String high = blank(highModel) ? this.lowModel : highModel.trim();
        this.highModel = high;
    }

    public String modelFor(String feature, boolean highTierAllowed) {
        if (highTierAllowed && feature != null && HIGH_FEATURES.contains(feature)) {
            return highModel;
        }
        return lowModel;
    }

    public String lowModel() {
        return lowModel;
    }

    public String highModel() {
        return highModel;
    }

    public boolean usesDistinctHighModel() {
        return !lowModel.equals(highModel);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
