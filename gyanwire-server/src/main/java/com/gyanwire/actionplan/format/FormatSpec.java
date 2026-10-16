package com.gyanwire.actionplan.format;

import java.util.List;
import java.util.Map;

public record FormatSpec(
        String id,
        String label,
        List<String> platforms,
        String aspect,
        String resolution,
        Integer maxSeconds,
        int[] targetSeconds,
        int[] scriptWords,
        int defaultBudgetMinutes,
        String stepTemplate,
        String moneyRuleId,
        boolean requiresDemo,
        List<Map<String, Object>> beats
) {
    public int scriptWordsMin() {
        return scriptWords == null || scriptWords.length == 0 ? 90 : scriptWords[0];
    }

    public int scriptWordsMax() {
        return scriptWords == null || scriptWords.length < 2 ? 130 : scriptWords[1];
    }
}
