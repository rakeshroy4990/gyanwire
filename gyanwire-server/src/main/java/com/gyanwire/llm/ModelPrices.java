package com.gyanwire.llm;

/**
 * Published token prices used only to compare quality per rupee.
 * A fixed dollar-to-rupee rate does not change which model wins.
 */
public final class ModelPrices {

    /** Fixed conversion so the report can show rupees without a live FX call. */
    public static final double USD_TO_INR = 84.0;

    private ModelPrices() {
    }

    public static Double usd(String model, Integer tokensIn, Integer tokensOut) {
        double[] rates = rates(model);
        if (rates == null || tokensIn == null || tokensOut == null) {
            return null;
        }
        double cost = (Math.max(0, tokensIn) * rates[0] + Math.max(0, tokensOut) * rates[1]) / 1_000_000d;
        return cost;
    }

    public static Double inr(String model, Integer tokensIn, Integer tokensOut) {
        Double dollars = usd(model, tokensIn, tokensOut);
        if (dollars == null) {
            return null;
        }
        return dollars * USD_TO_INR;
    }

    public static Double qualityPerRupee(double quality, Double costInr) {
        if (costInr == null || costInr <= 0 || Double.isNaN(quality)) {
            return null;
        }
        return quality / costInr;
    }

    private static double[] rates(String model) {
        if ("gpt-5.4".equals(model)) {
            return new double[] {2.5, 15.0};
        }
        if ("gpt-4o-mini".equals(model)) {
            return new double[] {0.15, 0.60};
        }
        return null;
    }
}
