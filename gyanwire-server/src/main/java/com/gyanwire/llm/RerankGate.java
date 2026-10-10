package com.gyanwire.llm;

import java.util.List;
import java.util.Map;

public final class RerankGate {

    public static final int LEAD = 8;

    private RerankGate() {
    }

    public static boolean shouldRerank(List<Map<String, Object>> results) {
        if (results == null || results.size() < 2) {
            return false;
        }
        return score(results.get(0)) - score(results.get(1)) < LEAD;
    }

    public static int score(Map<String, Object> row) {
        if (row == null) {
            return 0;
        }
        Object value = row.get("researchScore");
        if (!(value instanceof Number)) {
            value = row.get("score");
        }
        return value instanceof Number n ? n.intValue() : 0;
    }
}
