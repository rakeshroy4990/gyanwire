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
    void fyiHeadlinesAreCategorisedAndDroppedFromRank() {
        assertThat(SignalRules.detect(
                "FIIs dump Indian stocks for eight straight weeks: Is a reversal on the cards or more pain ahead?"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect(
                "National Formulary of India lists drugs not approved by CDSCO, DCGI seeks explanation from IPC"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("CDSCO approves a paediatric heart valve for clinics"))
                .isEqualTo("approval");
        assertThat(SignalRules.detect(
                "Brent commands a war premium as Middle East supply risks keep oil"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("HPCL launches a prepaid fuel card for truck fleets"))
                .isEqualTo("launch");
        assertThat(SignalRules.actionability("fyi")).isZero();

        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        NewsRanker.Ranked ranked = NewsRanker.rank(List.of(
                row("FIIs dump Indian stocks for eight straight weeks: Is a reversal on the cards or more pain ahead?",
                        now, false, 80, "fyi", "a.com"),
                row("National Formulary of India lists drugs not approved by CDSCO, DCGI seeks explanation from IPC",
                        now, false, 75, "approval", "b.com"),
                row("Sebi notifies new settlement rule; unveils formula, fast-track route. Check details",
                        now, false, 72, "launch", "d.com"),
                row("Broker cuts fees on equity delivery", now, false, 66, "pricing", "c.com"),
                row("Brent commands a war premium as Middle East supply risks keep oil",
                        now, false, 90, "other", "e.com")
        ), 5, "products", 0, 0, 0, 40);
        assertThat(ranked.items()).extracting(NewsRow::title).containsExactly("Broker cuts fees on equity delivery");
        assertThat(ranked.items()).noneMatch(item -> SignalRules.isFyi(item.signalType()));
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
                row("HPCL launches a prepaid fuel card for truck fleets", now, false, 70, "launch", "a.com"),
                row("HPCL launches a prepaid fuel card for truck fleets", now, false, 68, "launch", "b.com"),
                row("Broker cuts fees on equity delivery", now, false, 66, "pricing", "c.com")
        ), 5, "products", 0, 0, 0, 40);
        assertThat(sameStory.items()).extracting(NewsRow::title).containsExactly(
                "HPCL launches a prepaid fuel card for truck fleets",
                "Broker cuts fees on equity delivery");
    }

    @Test
    void rankShowsNewestFirstEvenWhenOlderScoreIsHigher() {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        NewsRow olderHigh = row("Older funding story", now.minus(Duration.ofDays(2)), false, 79, "funding", "uk.com");
        olderHigh.country("GB");
        NewsRow newerLower = row("Newer cash squeeze", now.minus(Duration.ofDays(1)), false, 55, "regulation", "in.com");
        newerLower.country("IN");
        NewsRanker.Ranked ranked = NewsRanker.rank(List.of(olderHigh, newerLower), 2, "products", 0, 0, 0, 40);
        assertThat(ranked.items()).extracting(NewsRow::title).containsExactly(
                "Newer cash squeeze",
                "Older funding story");
    }

    @Test
    void worldwideListLeadsWithIndiaAndKeepsOtherCountries() {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        List<NewsRow> pool = new ArrayList<>();
        String[][] indiaRows = {
                {"Exchange opens a new trading desk", "launch", "90"},
                {"Clinic raises a seed round", "funding", "89"},
                {"Regulator clears a heart device", "approval", "88"},
                {"Broker cuts delivery fees", "pricing", "87"},
        };
        int domain = 0;
        for (String[] spec : indiaRows) {
            NewsRow india = row(spec[0], now, false, Integer.parseInt(spec[2]), spec[1], "in" + domain + ".com");
            india.country("IN");
            pool.add(india);
            domain++;
        }
        NewsRow australia = row("Sydney lab limits smart glasses", now, false, 70, "launch", "au.com");
        australia.country("AU");
        NewsRow britain = row("London watchdog probes a platform", now, false, 60, "funding", "uk.com");
        britain.country("GB");
        pool.add(australia);
        pool.add(britain);
        NewsRanker.Ranked ranked = NewsRanker.rank(pool, 5, "products", 8, 0, 0, 40);
        assertThat(ranked.items()).hasSize(5);
        assertThat(ranked.items().get(0).country()).isEqualTo("IN");
        assertThat(ranked.items().stream().filter(item -> "IN".equals(item.country())).count()).isEqualTo(3);
        assertThat(ranked.items()).extracting(NewsRow::country).contains("AU", "GB");
    }

    @Test
    void refreshRulesScoresUncapsStoredFiftyFive() {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        NewsRow capped = row("Acme launches paediatric vitamin drops", now, false, 55, "launch", "a.com");
        capped.scoredBy("rules").specificity(80).indiaRelevance(80);
        NewsRow llm = row("Funded clinic SaaS", now, false, 55, "funding", "b.com");
        llm.scoredBy("llm").specificity(80).indiaRelevance(80);

        NewsProperties.Rank rank = new NewsProperties.Rank();
        rank.setRulesCap(100);
        NewsQueryService.refreshRulesScores(List.of(capped, llm), rank, now);

        assertThat(capped.opportunityScore()).isGreaterThan(55);
        assertThat(llm.opportunityScore()).isEqualTo(55);
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
