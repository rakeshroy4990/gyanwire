package com.gyanwire.research.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedDatesTest {

    @Test
    void parsesRssPubDate() {
        Instant instant = PublishedDates.parse("Fri, 09 Oct 2026 10:00:00 GMT");
        assertThat(instant).isNotNull();
        assertThat(PublishedDates.label(instant.toString())).isEqualTo("9 Oct 2026");
    }

    @Test
    void appliesAgeDays() {
        Map<String, Object> row = new HashMap<>();
        PublishedDates.apply(row, Instant.parse("2026-10-08T00:00:00Z"));
        assertThat(row.get("publishedLabel")).isEqualTo("8 Oct 2026");
        assertThat(((Number) row.get("ageDays")).intValue()).isGreaterThanOrEqualTo(0);
    }
}
