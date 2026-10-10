package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NewsEngineTest {

    @Test
    void startupSkipsAFreshIndustry() {
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        assertThat(FreshnessGate.shouldSkip(now.minus(Duration.ofMinutes(10)), now, Duration.ofMinutes(30))).isTrue();
        assertThat(FreshnessGate.shouldSkip(now.minus(Duration.ofMinutes(45)), now, Duration.ofMinutes(30))).isFalse();
        assertThat(FreshnessGate.shouldSkip(null, now, Duration.ofMinutes(30))).isFalse();
    }

    @Test
    void concurrentRequestsShareOneFetch() throws Exception {
        IndustryFetchGate gate = new IndustryFetchGate();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger calls = new AtomicInteger();
        try {
            var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> gate.share("Medical", Duration.ofSeconds(3), () -> {
                calls.incrementAndGet();
                sleep(250);
                return IndustryFetchGate.Outcome.done();
            }, pool));
            sleep(30);
            var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> gate.share("Medical", Duration.ofSeconds(3), () -> {
                calls.incrementAndGet();
                return IndustryFetchGate.Outcome.done();
            }, pool));
            assertThat(first.join().timedOut()).isFalse();
            assertThat(second.join().timedOut()).isFalse();
            assertThat(calls).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void timeoutReturnsWarmingUp() {
        IndustryFetchGate gate = new IndustryFetchGate();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            IndustryFetchGate.Outcome outcome = gate.share("IT", Duration.ofMillis(40), () -> {
                sleep(400);
                return IndustryFetchGate.Outcome.done();
            }, pool);
            assertThat(outcome.timedOut()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void seedNeverLoadsWhenRealRowsExist() {
        assertThat(SeedPolicy.shouldSeed(false, true, true, true, true)).isFalse();
        assertThat(SeedPolicy.shouldSeed(true, true, false, true, true)).isFalse();
        assertThat(SeedPolicy.shouldSeed(false, true, false, true, false)).isTrue();
        assertThat(SeedPolicy.shouldSeed(false, false, false, true, true)).isFalse();
    }

    @Test
    void aDeadFeedDoesNotStopTheRun() {
        List<String> ran = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SourceBatch.run(List.of(source("dead", true), source("live", true), source("off", false)), source -> {
            if ("dead".equals(source.name())) {
                throw new IllegalStateException("HTTP 500");
            }
            ran.add(source.name());
        }, ex -> errors.add(ex.getMessage()));
        assertThat(ran).containsExactly("live");
        assertThat(errors).containsExactly("HTTP 500");
    }

    @Test
    void titlesDatesAndGoogleWindowAreClean() {
        assertThat(NewsTexts.cleanTitle("New paediatric drops - News-Medical")).isEqualTo("New paediatric drops");
        assertThat(NewsTexts.cleanTitle("Bank circular | The Hindu")).isEqualTo("Bank circular");
        assertThat(NewsTexts.keepLanguage("CDSCO approves a device")).isTrue();
        assertThat(NewsTexts.keepLanguage("药品获批上市")).isFalse();
        String url = NewsUrls.apply(
                "https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en",
                "paediatric launch when:7d");
        assertThat(url).contains("when%3A7d");
        assertThat(url).doesNotContain("&when:");
        assertThat(url.indexOf("when%3A7d")).isLessThan(url.indexOf("&hl="));
    }

    @Test
    void nearDuplicatesCollapseAndStaleItemsAreLabelled() {
        assertThat(NewsTexts.nearDuplicate(
                "Acme launches paediatric vitamin drops",
                "Acme launches paediatric vitamin drops India")).isTrue();
        assertThat(NewsTexts.nearDuplicate(
                "Acme launches paediatric vitamin drops",
                "RBI issues a banking circular on UPI")).isFalse();

        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        NewsRow fresh = row("Fresh launch", now.minus(Duration.ofDays(2)), false, 80, "launch", "a.com");
        NewsRow month = row("Month old", now.minus(Duration.ofDays(20)), false, 70, "funding", "b.com");
        NewsRow old = row("Very old", now.minus(Duration.ofDays(40)), false, 90, "approval", "c.com");
        NewsRow ancient = row("Ancient", now.minus(Duration.ofDays(400)), false, 99, "launch", "d.com");
        NewsRow estimated = row("No date", now.minus(Duration.ofDays(1)), true, 95, "launch", "e.com");
        FreshnessGate.Result widened = FreshnessGate.select(List.of(fresh, month, old, ancient, estimated), 3, new int[]{14, 30, 60}, now);
        assertThat(widened.freshness()).isEqualTo("older — few recent items");
        assertThat(widened.items()).extracting(NewsRow::title).containsExactlyInAnyOrder("Fresh launch", "Month old", "Very old");
        assertThat(widened.items()).extracting(NewsRow::title).doesNotContain("Ancient", "No date");

        FreshnessGate.Result plenty = FreshnessGate.select(List.of(
                row("a", now.minus(Duration.ofDays(1)), false, 1, "launch", "a.com"),
                row("b", now.minus(Duration.ofDays(1)), false, 1, "funding", "b.com"),
                row("c", now.minus(Duration.ofDays(2)), false, 1, "approval", "c.com")
        ), 3, new int[]{14, 30, 60}, now);
        assertThat(plenty.freshness()).isEqualTo("fresh");
    }

    @Test
    void rankHidesWeakScoresAndMixesSignals() {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        List<NewsRow> pool = List.of(
                row("Launch one", now, false, 70, "launch", "a.com"),
                row("Launch two", now, false, 68, "launch", "a.com"),
                row("Launch three", now, false, 66, "launch", "a.com"),
                row("Funded", now, false, 64, "funding", "b.com"),
                row("Approved", now, false, 62, "approval", "c.com"),
                row("Priced", now, false, 60, "pricing", "f.com"),
                row("Study", now, false, 50, "research", "d.com"),
                row("Weak", now, false, 16, "opinion", "e.com")
        );
        NewsRanker.Ranked ranked = NewsRanker.rank(pool, 5, "products", 8, 8, 12, 40);
        assertThat(ranked.items()).hasSize(5);
        assertThat(ranked.items().stream().filter(item -> "a.com".equals(item.domain())).count()).isLessThanOrEqualTo(2);
        assertThat(ranked.items().stream().filter(item -> "research".equals(item.signalType())).findFirst()).isEmpty();
        NewsRanker.Ranked thin = NewsRanker.rank(List.of(
                row("Only launch", now, false, 80, "launch", "a.com"),
                row("Only fund", now, false, 55, "funding", "b.com"),
                row("Weak", now, false, 16, "opinion", "e.com")
        ), 5, "products", 0, 0, 0, 40);
        assertThat(thin.items()).anyMatch(NewsRow::weakSignal);
        NewsRow india = row("India", now, false, 50, "launch", "in.com");
        india.indiaRelevance(80);
        NewsRow global = row("Global", now, false, 50, "funding", "out.com");
        global.indiaRelevance(10);
        NewsRanker.Ranked order = NewsRanker.rank(List.of(global, india), 2, "products", 8, 0, 0, 40);
        assertThat(order.items().get(0).title()).isEqualTo("India");

        NewsRanker.Ranked sameStory = NewsRanker.rank(List.of(
                row("Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta Pandey", now, false, 70, "regulation", "a.com"),
                row("Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta Pandey", now, false, 68, "regulation", "b.com"),
                row("Broker cuts fees on equity delivery", now, false, 66, "pricing", "c.com")
        ), 5, "products", 0, 0, 0, 40);
        assertThat(sameStory.items()).extracting(NewsRow::title).containsExactly(
                "Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta Pandey",
                "Broker cuts fees on equity delivery");
    }

    private static NewsStore.SourceRow source(String name, boolean enabled) {
        return new NewsStore.SourceRow(
                java.util.UUID.randomUUID(), "Medical", null, "rss", name, "https://example.com/" + name,
                "IN", 1, enabled, null, null, 0);
    }

    private static NewsRow row(String title, Instant published, boolean estimated, int score, String signal, String domain) {
        return new NewsRow()
                .title(title)
                .publishedAt(published)
                .dateEstimated(estimated)
                .opportunityScore(score)
                .signalType(signal)
                .domain(domain)
                .indiaRelevance(10)
                .sub("Paediatrics");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
