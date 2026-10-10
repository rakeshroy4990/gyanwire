package com.gyanwire.llm;

public final class PlanPolicy {

    private PlanPolicy() {
    }

    public static ModelTier tierFor(String planCode, String stage, ModelTier requested) {
        String plan = planCode == null || planCode.isBlank() ? "anonymous" : planCode;
        if ("anonymous".equals(plan)) {
            return null;
        }
        ModelTier ask = requested == null ? ModelTier.LIGHT : requested;
        if (ask == ModelTier.DEEP) {
            ask = ModelTier.MAIN;
        }
        if ("free".equals(plan) || "student".equals(plan)) {
            return ModelTier.LIGHT;
        }
        if ("rerank".equals(stage) && !"pro".equals(plan) && !"team".equals(plan)) {
            return ModelTier.LIGHT;
        }
        if (("hardcase".equals(stage) || "plan_a".equals(stage)) && ("pro".equals(plan) || "team".equals(plan))) {
            return ModelTier.MAIN;
        }
        if ("pro".equals(plan) || "team".equals(plan)) {
            return ask;
        }
        return ModelTier.LIGHT;
    }
}
