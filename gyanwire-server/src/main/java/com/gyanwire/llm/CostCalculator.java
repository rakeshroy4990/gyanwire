package com.gyanwire.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CostCalculator {

    private CostCalculator() {
    }

    public static BigDecimal inr(String model, int inputTokens, int cachedInputTokens, int outputTokens, double fxInrPerUsd) {
        ModelCatalog.Price price = ModelCatalog.price(model);
        if (price == null) {
            return null;
        }
        int cached = Math.max(0, Math.min(cachedInputTokens, Math.max(0, inputTokens)));
        int fresh = Math.max(0, inputTokens) - cached;
        double usd = (fresh * price.inputPerMillion()
                + cached * price.cachedInputPerMillion()
                + Math.max(0, outputTokens) * price.outputPerMillion()) / 1_000_000d;
        double rupees = usd * (fxInrPerUsd <= 0 ? ModelCatalog.FX_INR_PER_USD : fxInrPerUsd);
        return BigDecimal.valueOf(rupees).setScale(6, RoundingMode.HALF_UP);
    }
}
