package com.gyanwire.ideas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdeaServiceDedupeTest {

    @Test
    void dedupeKeepsFirstPerPatternIdAndNeverRewritesFields() {
        List<Map<String, Object>> drafts = new ArrayList<>();
        drafts.add(idea("P1", "First title", "First offer", "₹199"));
        drafts.add(idea("P1", "Duplicate title", "Duplicate offer", "₹999"));
        drafts.add(idea("P2", "Second title", "Second offer", "₹499"));
        drafts.add(idea("", "No pattern", "Keep me", "₹99"));

        List<Map<String, Object>> out = IdeaService.dedupeByPatternId(drafts);

        assertThat(out).hasSize(3);
        assertThat(out.get(0).get("title")).isEqualTo("First title");
        assertThat(out.get(0).get("offer")).isEqualTo("First offer");
        assertThat(out.get(0).get("price")).isEqualTo("₹199");
        assertThat(out.get(1).get("title")).isEqualTo("Second title");
        assertThat(out.get(2).get("title")).isEqualTo("No pattern");
    }

    private static Map<String, Object> idea(String patternId, String title, String offer, String price) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (patternId != null && !patternId.isBlank()) {
            row.put("pattern_id", patternId);
        }
        row.put("title", title);
        row.put("offer", offer);
        row.put("price", price);
        return row;
    }
}
