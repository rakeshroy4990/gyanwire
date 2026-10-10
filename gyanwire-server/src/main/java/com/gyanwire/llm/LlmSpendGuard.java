package com.gyanwire.llm;

import java.math.BigDecimal;

public final class LlmSpendGuard {

    private LlmSpendGuard() {
    }

    /** Null means skip the model call and use cache or a template. */
    public static ModelTier degrade(ModelTier tier, BigDecimal spentInr, BigDecimal capInr) {
        if (tier == null) {
            return null;
        }
        BigDecimal spent = spentInr == null ? BigDecimal.ZERO : spentInr;
        BigDecimal cap = capInr == null ? BigDecimal.valueOf(ModelCatalog.DAILY_CAP_INR) : capInr;
        if (cap.signum() <= 0) {
            return tier;
        }
        if (spent.compareTo(cap) >= 0) {
            return null;
        }
        BigDecimal warn = cap.multiply(new BigDecimal("0.8"));
        if (spent.compareTo(warn) >= 0 && (tier == ModelTier.MAIN || tier == ModelTier.DEEP)) {
            return ModelTier.LIGHT;
        }
        return tier;
    }
}
