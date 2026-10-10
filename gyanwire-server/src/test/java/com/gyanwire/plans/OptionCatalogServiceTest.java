package com.gyanwire.plans;

import com.gyanwire.persistence.FlowStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OptionCatalogServiceTest {

    @Test
    void filtersByTaskTypeAndOrdersFreeFirst() {
        FlowStore store = mock(FlowStore.class);
        when(store.catalogItems()).thenReturn(List.of(
                item("paid-x", "Paid X", 500, "monthly", "coding"),
                item("free-x", "Free X", 0, "free", "coding"),
                item("writer", "Writer", 0, "free", "writing")
        ));
        OptionCatalogService service = new OptionCatalogService(store);
        List<Map<String, Object>> coding = service.list("coding");
        assertThat(coding).extracting(m -> m.get("id")).containsExactly("free-x", "paid-x");
        assertThat(service.list("writing")).extracting(m -> m.get("id")).containsExactly("writer");
    }

    @Test
    void passesCatalogUrlAndBlurb() {
        FlowStore store = mock(FlowStore.class);
        Map<String, Object> swayam = item("swayam", "SWAYAM starter course", 0, "free", "learning");
        swayam.put("url", "https://swayam.gov.in");
        swayam.put("blurb", "India’s free government learning platform.");
        when(store.catalogItems()).thenReturn(List.of(swayam));
        OptionCatalogService service = new OptionCatalogService(store);
        Map<String, Object> row = service.list("learning").get(0);
        assertThat(row.get("url")).isEqualTo("https://swayam.gov.in");
        assertThat(row.get("blurb")).asString().contains("government learning");
    }

    private static Map<String, Object> item(String id, String name, int cost, String billing, String taskType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("costInr", cost);
        row.put("billing", billing);
        row.put("kind", "tool");
        row.put("taskFit", Map.of(taskType, Map.of(
                "speedup", List.of(cost == 0 ? 1.0 : 1.1, cost == 0 ? 1.0 : 1.2),
                "confidence", cost == 0 ? "baseline" : "placeholder"
        )));
        row.put("pricedAt", "2026-10-09");
        row.put("stale", false);
        row.put("sampleCount", 0);
        row.put("bucket", "tools");
        row.put("skills", List.of("ide"));
        return row;
    }
}
