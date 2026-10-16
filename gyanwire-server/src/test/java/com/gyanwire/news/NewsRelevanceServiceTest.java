package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsRelevanceServiceTest {

    @Test
    void dropsDeniedKeywordsAndOffDomain() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsRow gossip = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Celebrity arrested after match score drama")
                .domain("medianama.com")
                .publishedAt(now.minusSeconds(3600));
        NewsRow ok = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Instagram launches free tier API for creators")
                .domain("medianama.com")
                .summary("Platform rolls out new SDK")
                .publishedAt(now.minusSeconds(3600));
        NewsRow off = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Random site launches tool")
                .domain("gossip.example")
                .publishedAt(now.minusSeconds(3600));
        List<NewsRow> gated = service.gate("Social Media", List.of(gossip, ok, off), now, Set.of());
        assertEquals(1, gated.size());
        assertEquals(ok.title(), gated.get(0).title());
        assertTrue(gated.get(0).relevanceReason().toLowerCase().contains("signal")
                || gated.get(0).relevanceReason().toLowerCase().contains("opportunity"));
        assertFalse(gated.get(0).signals().isEmpty());
    }

    @Test
    void freshnessPointsDecay() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertEquals(25, NewsRelevanceService.freshnessPoints(now.minusSeconds(3600), now));
        assertEquals(10, NewsRelevanceService.freshnessPoints(now.minusSeconds(96 * 3600L), now));
        assertEquals(0, NewsRelevanceService.freshnessPoints(now.minusSeconds(40 * 24 * 3600L), now));
    }

    @Test
    void packLoadsSocialMedia() {
        NewsPackRegistry packs = new NewsPackRegistry();
        assertTrue(packs.pack("Social Media").isPresent());
        Map<String, List<String>> signals = packs.pack("Social Media").get().signals();
        assertTrue(signals.containsKey("launch"));
    }
}
