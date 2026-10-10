package com.gyanwire.research.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchEngineServiceRankTest {

    @Test
    void rankByIdeaScoreDropsZeroAndSortsDescending() {
        Map<String, Object> high = new HashMap<>();
        high.put("url", "https://a.example/high");
        high.put("score", 72);
        Map<String, Object> mid = new HashMap<>();
        mid.put("url", "https://a.example/mid");
        mid.put("score", 41);
        Map<String, Object> none = new HashMap<>();
        none.put("url", "https://a.example/none");
        none.put("score", 0);
        Map<String, Object> missing = new HashMap<>();
        missing.put("url", "https://a.example/missing");

        List<Map<String, Object>> ranked = SearchEngineService.rankByIdeaScore(List.of(mid, none, high, missing));

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).get("url")).isEqualTo("https://a.example/high");
        assertThat(ranked.get(0).get("id")).isEqualTo("r-1");
        assertThat(ranked.get(1).get("url")).isEqualTo("https://a.example/mid");
        assertThat(ranked.get(1).get("id")).isEqualTo("r-2");
    }
}
