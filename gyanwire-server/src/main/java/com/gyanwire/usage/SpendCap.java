package com.gyanwire.usage;

public final class SpendCap {

    private SpendCap() {
    }

    public static double inr(int tokensIn, int tokensOut, double inrPerMillionIn, double inrPerMillionOut) {
        return (Math.max(0, tokensIn) * inrPerMillionIn + Math.max(0, tokensOut) * inrPerMillionOut) / 1_000_000d;
    }

    public static boolean allowed(double spentInr, double capInr) {
        if (capInr <= 0) {
            return true;
        }
        return spentInr < capInr;
    }

    public static double capFor(String planId) {
        return switch (planId == null ? "free" : planId) {
            case "pro" -> 200;
            case "team" -> 800;
            case "student" -> 40;
            case "anonymous" -> 5;
            default -> 15;
        };
    }
}
