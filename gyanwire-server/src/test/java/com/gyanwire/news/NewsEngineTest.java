package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NewsEngineTest {

    @Test
    void aiLabChipKeepsThatLabsFeatureLaunches() {
        NewsRow gpt = new NewsRow().title("OpenAI rolls out GPT-6 Intelligent UI");
        NewsRow claude = new NewsRow().title("Anthropic launches Claude Haiku 5.5");
        NewsRow grok = new NewsRow().title("xAI launches Grok 4.7 for coding");
        assertThat(NewsQueryService.labRows("AI", "Grok", List.of(gpt, claude, grok)))
                .extracting(NewsRow::title)
                .containsExactly("xAI launches Grok 4.7 for coding");
        assertThat(NewsQueryService.labRows("AI", null, List.of(gpt, claude))).hasSize(2);
    }

    @Test
    void medicalSubChipDropsParentMedicalNoise() {
        NewsRow paediatric = new NewsRow()
                .title("CDSCO approves a paediatric heart valve for clinics");
        NewsRow tagged = new NewsRow()
                .sub("Paediatrics")
                .title("Hospital expands NICU capacity in Bengaluru");
        NewsRow devices = new NewsRow()
                .title("NITI Aayog explores policies to boost India's medical and dental device manufacturing industry");
        NewsRow celebrityEcg = new NewsRow()
                .title("Puneeth Rajkumar’s doctor recalls his ECG showing ‘strain’: ‘He did kickboxing for one and a half hours’");
        NewsRow celebrityTumour = new NewsRow()
                .title("Saba Ali Khan recalls brain tumour diagnosis at 27 after suddenly fainting: ‘Thought it was my stomach’");
        NewsRow cardiac = new NewsRow()
                .title("FDA clears a new coronary stent for heart failure clinics");

        assertThat(NewsQueryService.labRows(
                "Medical", "Paediatrics", List.of(paediatric, tagged, devices, celebrityEcg, celebrityTumour, cardiac)))
                .extracting(NewsRow::title)
                .containsExactly(
                        "CDSCO approves a paediatric heart valve for clinics",
                        "Hospital expands NICU capacity in Bengaluru");
        assertThat(NewsQueryService.labRows("Medical", "Cardiology", List.of(devices, cardiac, paediatric)))
                .extracting(NewsRow::title)
                .containsExactly(
                        "FDA clears a new coronary stent for heart failure clinics",
                        "CDSCO approves a paediatric heart valve for clinics");
        assertThat(NewsQueryService.labRows("Medical", null, List.of(devices, celebrityEcg))).hasSize(2);
    }

    @Test
    void everyIndustrySubChipDropsGenericParentNews() {
        NewsRow genericIt = new NewsRow().title("Enterprise SaaS platform raises Series B for India expansion");
        NewsRow chips = new NewsRow().title("NVIDIA launches new AI GPU accelerator for data centers");
        assertThat(NewsQueryService.labRows("IT", "Semiconductors", List.of(genericIt, chips)))
                .extracting(NewsRow::title)
                .containsExactly("NVIDIA launches new AI GPU accelerator for data centers");

        NewsRow genericSpace = new NewsRow().title("ISRO shares annual space sector outlook for India");
        NewsRow lunar = new NewsRow().title("NASA Artemis lunar lander clears key mission review");
        assertThat(NewsQueryService.labRows("Space", "Lunar", List.of(genericSpace, lunar)))
                .extracting(NewsRow::title)
                .containsExactly("NASA Artemis lunar lander clears key mission review");

        NewsRow marketWrap = new NewsRow().title("Sensex and Nifty close mixed as FIIs stay cautious");
        NewsRow evStock = new NewsRow().title("Tata Motors EV deliveries jump; Ola Electric share catalyst");
        assertThat(NewsQueryService.labRows("Share Market", "EV", List.of(marketWrap, evStock)))
                .extracting(NewsRow::title)
                .containsExactly("Tata Motors EV deliveries jump; Ola Electric share catalyst");

        NewsRow genericSocial = new NewsRow().title("Meta updates community guidelines across its apps");
        NewsRow reels = new NewsRow().title("Instagram Reels rolls out new monetisation for short video creators");
        assertThat(NewsQueryService.labRows("Social Media", "Short Video", List.of(genericSocial, reels)))
                .extracting(NewsRow::title)
                .containsExactly("Instagram Reels rolls out new monetisation for short video creators");

        NewsRow genericGame = new NewsRow().title("Gaming industry revenue outlook revised for India");
        NewsRow console = new NewsRow().title("Sony PlayStation unveils new PS5 exclusive console title");
        assertThat(NewsQueryService.labRows("Gaming", "Console", List.of(genericGame, console)))
                .extracting(NewsRow::title)
                .containsExactly("Sony PlayStation unveils new PS5 exclusive console title");

        NewsRow genericRe = new NewsRow().title("India real estate sector sees strong investor interest");
        NewsRow rera = new NewsRow().title("Maharashtra RERA issues new registration guidelines for housing projects");
        assertThat(NewsQueryService.labRows("Real Estate", "RERA", List.of(genericRe, rera)))
                .extracting(NewsRow::title)
                .containsExactly("Maharashtra RERA issues new registration guidelines for housing projects");

        NewsRow genericAstro = new NewsRow().title("Astrology apps see festive season surge in India");
        NewsRow tarot = new NewsRow().title("New tarot reading app launches deck guides for beginners");
        assertThat(NewsQueryService.labRows("Astrology", "Tarot", List.of(genericAstro, tarot)))
                .extracting(NewsRow::title)
                .containsExactly("New tarot reading app launches deck guides for beginners");
    }

    @Test
    void browseListsNewestFirstAcrossThirtyDaysWithPagination() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsRow today = row("Today launch", now.minus(Duration.ofHours(6)), false, 70, "launch", "a.com");
        NewsRow week = row("Week launch", now.minus(Duration.ofDays(5)), false, 90, "launch", "b.com");
        NewsRow month = row("Month launch", now.minus(Duration.ofDays(20)), false, 95, "launch", "c.com");
        NewsRow estimated = row("Estimated", now, true, 99, "launch", "d.com");
        List<NewsRow> ordered = NewsQueryService.chronological(List.of(month, estimated, today, week));
        assertThat(ordered).extracting(NewsRow::title)
                .containsExactly("Today launch", "Week launch", "Month launch");
        NewsQueryService.PageSlice first = NewsQueryService.slice(ordered, 1, 2);
        assertThat(first.items()).extracting(NewsRow::title).containsExactly("Today launch", "Week launch");
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.hasMore()).isTrue();
        NewsQueryService.PageSlice second = NewsQueryService.slice(ordered, 2, 2);
        assertThat(second.items()).extracting(NewsRow::title).containsExactly("Month launch");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void industryWindowsChunkTowardThirtyDays() {
        assertThat(NewsQueryService.windowsFor(30)).containsExactly(1, 7, 30);
        assertThat(NewsQueryService.windowsFor(7)).containsExactly(1, 7);
        assertThat(NewsQueryService.windowsFor(1)).containsExactly(1);
        assertThat(FreshnessGate.labelForDays(1)).isEqualTo("fresh");
        assertThat(FreshnessGate.labelForDays(7)).isEqualTo("last 7 days");
        assertThat(FreshnessGate.labelForDays(30)).isEqualTo("last 30 days");
    }

    @Test
    void freshnessGateStillWidensWhenAsked() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsRow today = row("Today launch", now.minus(Duration.ofHours(6)), false, 70, "launch", "a.com");
        NewsRow week = row("Week launch", now.minus(Duration.ofDays(5)), false, 70, "launch", "b.com");
        NewsRow month = row("Month launch", now.minus(Duration.ofDays(20)), false, 70, "launch", "c.com");
        FreshnessGate.Result dayFirst = FreshnessGate.select(List.of(today, week, month), 1, new int[]{1, 7, 30}, now);
        assertThat(dayFirst.freshness()).isEqualTo("fresh");
        FreshnessGate.Result weekWiden = FreshnessGate.select(List.of(week, month), 1, new int[]{1, 7, 30}, now);
        assertThat(weekWiden.freshness()).isEqualTo("last 7 days");
        FreshnessGate.Result monthWiden = FreshnessGate.select(List.of(month), 1, new int[]{1, 7, 30}, now);
        assertThat(monthWiden.freshness()).isEqualTo("last 30 days");
    }

    @Test
    void firstPagePaintsTodayBeforeFillingFromWiderWindows() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsRow today = row("Today launch", now.minus(Duration.ofHours(6)), false, 70, "launch", "a.com");
        NewsRow week = row("Week launch", now.minus(Duration.ofDays(5)), false, 90, "launch", "b.com");
        NewsRow month = row("Month launch", now.minus(Duration.ofDays(20)), false, 95, "launch", "c.com");
        List<NewsRow> ordered = List.of(today, week, month);
        NewsQueryService.FirstPage page = NewsQueryService.firstPage(ordered, 10, now);
        assertThat(page.freshness()).isEqualTo("fresh");
        assertThat(page.windowDays()).isEqualTo(1);
        assertThat(page.paintFirst()).isEqualTo(1);
        assertThat(page.items()).extracting(NewsRow::title)
                .containsExactly("Today launch", "Week launch", "Month launch");
    }

    @Test
    void firstPageUsesWeekWhenTodayIsEmpty() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsRow week = row("Week launch", now.minus(Duration.ofDays(3)), false, 70, "launch", "b.com");
        NewsRow month = row("Month launch", now.minus(Duration.ofDays(20)), false, 95, "launch", "c.com");
        NewsQueryService.FirstPage page = NewsQueryService.firstPage(List.of(week, month), 10, now);
        assertThat(page.freshness()).isEqualTo("last 7 days");
        assertThat(page.windowDays()).isEqualTo(7);
        assertThat(page.paintFirst()).isEqualTo(1);
        assertThat(page.items()).extracting(NewsRow::title)
                .containsExactly("Week launch", "Month launch");
    }

    @Test
    void firstPageReportsWarmingUpWhenNothingIsStored() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsQueryService.FirstPage page = NewsQueryService.firstPage(List.of(), 10, now);
        assertThat(page.freshness()).isEqualTo("warming_up");
        assertThat(page.items()).isEmpty();
        assertThat(page.paintFirst()).isZero();
    }

    @Test
    void firstPageReportsEmptyMonthWhenIndustryIsStockedButChipIsEmpty() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        NewsQueryService.FirstPage page = NewsQueryService.firstPage(List.of(), 10, now, false);
        assertThat(page.freshness()).isEqualTo("last 30 days");
        assertThat(page.items()).isEmpty();
        assertThat(page.paintFirst()).isZero();
    }

    @Test
    void anthropicChipKeepsClaudeLaunchesOnly() {
        NewsRow gpt = new NewsRow().title("OpenAI rolls out GPT-6 Intelligent UI");
        NewsRow claude = new NewsRow().title("Anthropic launches Claude Haiku 5.5");
        NewsRow gemini = new NewsRow().title("Google unveils Gemini agent tools");
        assertThat(NewsQueryService.labRows("AI", "Anthropic", List.of(gpt, claude, gemini)))
                .extracting(NewsRow::title)
                .containsExactly("Anthropic launches Claude Haiku 5.5");
    }

    @Test
    void dismissedIdsAndUrlsAreDroppedBeforeRank() {
        UUID keptId = UUID.randomUUID();
        UUID goneId = UUID.randomUUID();
        NewsRow kept = row("Kept launch", Instant.parse("2026-10-16T00:00:00Z"), false, 70, "launch", "a.com")
                .id(keptId).url("https://a.example/kept");
        NewsRow byId = row("Gone by id", Instant.parse("2026-10-16T00:00:00Z"), false, 70, "launch", "b.com")
                .id(goneId).url("https://b.example/gone");
        NewsRow byUrl = row("Gone by url", Instant.parse("2026-10-16T00:00:00Z"), false, 70, "launch", "c.com")
                .id(UUID.randomUUID()).url("https://c.example/gone");
        NewsStore.Dismissals dismissals = new NewsStore.Dismissals(
                Set.of(goneId),
                Set.of("https://c.example/gone"));
        assertThat(NewsQueryService.withoutDismissed(List.of(kept, byId, byUrl), dismissals))
                .extracting(NewsRow::title)
                .containsExactly("Kept launch");
    }

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
        assertThat(NewsTexts.nearDuplicate(
                "Today’s Gold Rate in India October 16: Gold prices steady in Coimbatore, Nagpur, Visakhapatnam, Surat, Jaipur",
                "Today’s Gold Rate in India October 16: Gold prices steady in Delhi, Mumbai, Kolkata, Chennai, Bengaluru"))
                .isTrue();
        assertThat(NewsTexts.templateKey(
                "Today’s Gold Rate in India October 16: Gold prices steady in Delhi, Mumbai, Kolkata, Chennai, Bengaluru"))
                .doesNotContain("delhi")
                .doesNotContain("october");

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
