package com.gyanwire.llm;

/**
 * Phase-1 models compiled into the image. Astra is not a default.
 * DEEP uses the same model as MAIN so a missing Cloud Run env var cannot call Astra.
 */
public final class ModelCatalog {

    public static final String LIGHT_MODEL = "gpt-6-luna";
    public static final String MAIN_MODEL = "gpt-6.1-sol";
    public static final String DEEP_MODEL = "gpt-6.1-sol";
    public static final double FX_INR_PER_USD = 96.5;
    public static final double DAILY_CAP_INR = 300;

    private ModelCatalog() {
    }

    public record Price(double inputPerMillion, double cachedInputPerMillion, double outputPerMillion) {
    }

    public static String model(ModelTier tier) {
        if (tier == null) {
            return LIGHT_MODEL;
        }
        return switch (tier) {
            case LIGHT -> LIGHT_MODEL;
            case MAIN, DEEP -> MAIN_MODEL;
        };
    }

    public static String effort(ModelTier tier) {
        if (tier == null) {
            return "low";
        }
        return switch (tier) {
            case LIGHT -> "low";
            case MAIN -> "medium";
            case DEEP -> "high";
        };
    }

    public static Price price(String model) {
        if (LIGHT_MODEL.equals(model)) {
            return new Price(0.10, 0.01, 0.50);
        }
        if (MAIN_MODEL.equals(model) || DEEP_MODEL.equals(model)) {
            return new Price(2.00, 0.10, 10.00);
        }
        return null;
    }
}
