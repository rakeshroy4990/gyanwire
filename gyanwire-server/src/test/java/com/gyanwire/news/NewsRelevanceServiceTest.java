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
        assertTrue(gated.get(0).relevanceReason().toLowerCase().contains("could pay"));
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
        NewsPackRegistry.Pack pack = packs.pack("Social Media").get();
        Map<String, List<String>> signals = pack.signals();
        assertTrue(signals.containsKey("launch"));
        assertTrue(pack.requireSignalMatch());
        assertFalse(pack.requireKeywords().isEmpty());
    }

    @Test
    void packLoadsGitHub() {
        NewsPackRegistry packs = new NewsPackRegistry();
        assertTrue(packs.pack("GitHub").isPresent());
        NewsPackRegistry.Pack pack = packs.pack("GitHub").get();
        assertTrue(pack.requireSignalMatch());
        assertTrue(pack.domainAllowed("github.com"));
        assertTrue(pack.signals().containsKey("stars"));
        assertTrue(pack.signals().containsKey("tool"));
    }

    @Test
    void githubKeepsFamousReposAndDropsOtherDomains() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");

        NewsRow react = new NewsRow()
                .id(UUID.randomUUID())
                .industry("GitHub")
                .title("facebook/react — The library for web and native user interfaces")
                .domain("github.com")
                .summary("Open-source GitHub project with 240.0k stars. Famous library/framework/tool.")
                .signalType("stars")
                .publishedAt(now.minusSeconds(3600));
        NewsRow blog = new NewsRow()
                .id(UUID.randomUUID())
                .industry("GitHub")
                .title("Why React won the frontend wars")
                .domain("techcrunch.com")
                .summary("Opinion piece about JavaScript frameworks")
                .publishedAt(now.minusSeconds(1800));

        List<NewsRow> gated = service.gate("GitHub", List.of(react, blog), now, Set.of());
        assertEquals(1, gated.size());
        assertTrue(gated.get(0).title().contains("facebook/react"));
        assertTrue(gated.get(0).relevanceScore() >= 15);
    }

    @Test
    void socialMediaKeepsPlatformLaunchesAndDropsCarsAndLawsuits() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertTrue(packs.pack("Social Media").get().requireSignalMatch());

        NewsRow instagram = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Instagram launches free tier API for creators")
                .domain("medianama.com")
                .summary("Platform rolls out new SDK for Reels")
                .publishedAt(now.minusSeconds(3600));
        NewsRow youtube = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("YouTube unveils Shorts monetisation guidelines in India")
                .domain("theverge.com")
                .summary("Creator fund policy update")
                .publishedAt(now.minusSeconds(4000));
        NewsRow mercedes = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Mercedes-AMG’s first ground-up SUV is a 1,150hp electric beast")
                .domain("theverge.com")
                .summary("The performance brand unveils its first dedicated electric SUV")
                .publishedAt(now.minusSeconds(1800));
        NewsRow muskLawsuit = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Social Media")
                .title("Elon Musk settles lawsuits over $1 million voter giveaway")
                .domain("techcrunch.com")
                .summary("Court filings announce a settlement in the giveaway case")
                .publishedAt(now.minusSeconds(2000));

        List<NewsRow> gated = service.gate(
                "Social Media",
                List.of(instagram, youtube, mercedes, muskLawsuit),
                now,
                Set.of());
        assertEquals(2, gated.size());
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("Instagram")));
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("YouTube")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("mercedes")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("musk")));
    }

    @Test
    void aiKeepsModelLaunchesAndDropsFitnessOriginStories() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertTrue(packs.pack("AI").isPresent());
        assertTrue(packs.pack("AI").get().requireSignalMatch());

        NewsRow claude = new NewsRow()
                .id(UUID.randomUUID())
                .industry("AI")
                .title("Anthropic launches Claude Haiku with faster coding")
                .domain("anthropic.com")
                .summary("New Claude model rolls out to API customers")
                .publishedAt(now.minusSeconds(3600));
        NewsRow openai = new NewsRow()
                .id(UUID.randomUUID())
                .industry("AI")
                .title("OpenAI unveils GPT feature for ChatGPT workspace")
                .domain("openai.com")
                .summary("ChatGPT rolls out a new AI agent tool")
                .publishedAt(now.minusSeconds(4000));
        NewsRow boldfit = new NewsRow()
                .id(UUID.randomUUID())
                .industry("AI")
                .title("From Boldfit To Origin")
                .domain("inc42.com")
                .summary("The origin story of a D2C fitness brand and sportswear push")
                .publishedAt(now.minusSeconds(1800));
        NewsRow emailNoise = new NewsRow()
                .id(UUID.randomUUID())
                .industry("AI")
                .title("Paid email training platform raises series A")
                .domain("inc42.com")
                .summary("Edtech startup announces funding for workplace training")
                .publishedAt(now.minusSeconds(2000));

        List<NewsRow> gated = service.gate(
                "AI",
                List.of(claude, openai, boldfit, emailNoise),
                now,
                Set.of());
        assertEquals(2, gated.size());
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("Claude")));
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("OpenAI")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("boldfit")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("email")));
    }

    @Test
    void realEstateKeepsPropertyPricesAndDropsPayCommissionAndLoanRates() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertTrue(packs.pack("Real Estate").isPresent());
        assertTrue(packs.pack("Real Estate").get().requireSignalMatch());

        NewsRow flatPrices = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Real Estate")
                .title("Flat prices rise 8% in Bengaluru; per sq ft hits new high")
                .domain("economictimes.indiatimes.com")
                .summary("Apartment prices and capital values firm across the city")
                .publishedAt(now.minusSeconds(3600));
        NewsRow villaPrices = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Real Estate")
                .title("Villa prices in Pune climb as inventory tightens")
                .domain("housing.com")
                .summary("Property prices for villas up year on year")
                .publishedAt(now.minusSeconds(4000));
        NewsRow launch = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Real Estate")
                .title("Builder opens booking for new apartment project in Mumbai")
                .domain("magicbricks.com")
                .summary("Residential project soft launch this week")
                .publishedAt(now.minusSeconds(5000));
        NewsRow payCommission = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Real Estate")
                .title("8th pay commission: DA merger, salary hike, fitment factor, inflation adjustment")
                .domain("livemint.com")
                .summary("Housing allowance and HRA hike expectations")
                .signalType("pricing")
                .publishedAt(now.minusSeconds(1800));
        NewsRow homeLoan = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Real Estate")
                .title("Home loan interest rates cut after RBI repo rate move")
                .domain("economictimes.indiatimes.com")
                .summary("EMI and MCLR update for housing loans")
                .signalType("pricing")
                .publishedAt(now.minusSeconds(2000));

        List<NewsRow> gated = service.gate(
                "Real Estate",
                List.of(flatPrices, villaPrices, launch, payCommission, homeLoan),
                now,
                Set.of());
        assertEquals(3, gated.size());
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("Flat prices")));
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("Villa prices")));
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("opens booking")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("pay commission")));
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("home loan")));
    }

    @Test
    void medicalDropsCelebrityHealthRecalls() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertTrue(packs.pack("Medical").isPresent());

        NewsRow ok = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Medical")
                .title("CDSCO approves paediatric vaccine for childhood RSV")
                .domain("thehindu.com")
                .summary("Regulator announces approval for a new child health vaccine")
                .publishedAt(now.minusSeconds(3600));
        NewsRow celebrity = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Medical")
                .title("Puneeth Rajkumar’s doctor recalls his ECG showing strain after kickboxing")
                .domain("indianexpress.com")
                .summary("Celebrity health story")
                .publishedAt(now.minusSeconds(1800));

        List<NewsRow> gated = service.gate("Medical", List.of(ok, celebrity), now, Set.of());
        assertEquals(1, gated.size());
        assertTrue(gated.get(0).title().contains("paediatric vaccine"));
    }

    @Test
    void shareMarketDropsGoldRateRoundupsAndCollapsesCityListDupes() {
        NewsPackRegistry packs = new NewsPackRegistry();
        NewsRelevanceService service = new NewsRelevanceService(packs, null);
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertTrue(packs.pack("Share Market").isPresent());

        NewsRow goldA = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Share Market")
                .title("Today’s Gold Rate in India October 16: Gold prices steady in Coimbatore, Nagpur, Visakhapatnam, Surat, Jaipur")
                .domain("economictimes.indiatimes.com")
                .signalType("pricing")
                .publishedAt(now.minusSeconds(3600));
        NewsRow goldB = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Share Market")
                .title("Today’s Gold Rate in India October 16: Gold prices steady in Delhi, Mumbai, Kolkata, Chennai, Bengaluru")
                .domain("livemint.com")
                .signalType("pricing")
                .publishedAt(now.minusSeconds(4000));
        NewsRow petrolA = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Share Market")
                .title("Petrol prices steady in Coimbatore, Nagpur, Visakhapatnam, Surat, Jaipur")
                .domain("moneycontrol.com")
                .summary("Retail fuel pricing update for several cities")
                .publishedAt(now.minusSeconds(5000));
        NewsRow petrolB = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Share Market")
                .title("Petrol prices steady in Delhi, Mumbai, Kolkata, Chennai, Bengaluru")
                .domain("business-standard.com")
                .summary("Retail fuel pricing update for metro cities")
                .publishedAt(now.minusSeconds(5200));
        NewsRow launch = new NewsRow()
                .id(UUID.randomUUID())
                .industry("Share Market")
                .title("Zerodha launches a new mutual fund screener for retail investors")
                .domain("moneycontrol.com")
                .summary("Broker rolls out an app screener tool")
                .publishedAt(now.minusSeconds(1800));

        List<NewsRow> gated = service.gate(
                "Share Market",
                List.of(goldA, goldB, petrolA, petrolB, launch),
                now,
                Set.of());
        assertEquals(2, gated.size());
        assertTrue(gated.stream().anyMatch(row -> row.title().contains("Zerodha")));
        assertEquals(1, gated.stream().filter(row -> row.title().toLowerCase().contains("petrol")).count());
        assertFalse(gated.stream().anyMatch(row -> row.title().toLowerCase().contains("gold")));
    }
}
