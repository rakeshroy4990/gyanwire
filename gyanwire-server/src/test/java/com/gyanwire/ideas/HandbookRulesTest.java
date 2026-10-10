package com.gyanwire.ideas;

import com.gyanwire.digests.DigestDeduper;
import com.gyanwire.eval.EvalMetrics;
import com.gyanwire.plans.OutlineDraft;
import com.gyanwire.plans.SkillBudgetPlanner;
import com.gyanwire.plans.WeeklyPlanner;
import com.gyanwire.projects.SimplePdf;
import com.gyanwire.referrals.ReferralCodes;
import com.gyanwire.research.brief.CitedBriefs;
import com.gyanwire.research.brief.ClaimChecks;
import com.gyanwire.research.brief.LanguageDetector;
import com.gyanwire.research.industry.IndustryModes;
import com.gyanwire.research.llm.LlmOutputs;
import com.gyanwire.research.rank.HybridRanker;
import com.gyanwire.sources.SourcePackCatalog;
import com.gyanwire.usage.SpendCap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.research.engine.PointersService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HandbookRulesTest {

    @Test
    void scorerShiftsByPersonaAndStaysInRange() {
        IdeaScorer.Inputs inputs = new IdeaScorer.Inputs(0.8, 0.7, 0.6, 0.5, 0.9, 0.2, 0.8, 0.1, 0.7, 0.8);
        int student = IdeaScorer.score("student", inputs).score();
        int working = IdeaScorer.score("working", inputs).score();
        int founder = IdeaScorer.score("founder", inputs).score();
        assertThat(student).isBetween(0, 100);
        assertThat(working).isBetween(0, 100);
        assertThat(founder).isBetween(0, 100);
        assertThat(IdeaScorer.score("student", inputs).drivers()).hasSize(3);
    }

    @Test
    void hardFiltersRejectStockTipsAndDiagnosis() {
        assertThat(FitFilter.decide("Share Market", "working", "stock tip buy the stock", 0, 10000, true, 2, 10, false).keep()).isFalse();
        assertThat(FitFilter.decide("Medical", "working", "diagnosis and dosage", 0, 10000, true, 2, 10, false).keep()).isFalse();
        assertThat(FitFilter.decide("IT", "student", "education course about tools", 8000, 5000, false, 4, 10, false).reason()).isEqualTo("student-capital");
        assertThat(FitFilter.decide("IT", "working", "a small service education", 1000, 25000, true, 4, 10, false).keep()).isTrue();
    }

    @Test
    void skillBudgetRespectsBandsAndFreePath() {
        assertThat(SkillBudgetPlanner.budget("30_50", 10)).isEqualTo(4000);
        assertThat(SkillBudgetPlanner.budgetFromProxy(30000, 10)).isEqualTo(3000);
        SkillBudgetPlanner.Plan free = SkillBudgetPlanner.plan("learn", 0, List.of(
                new SkillBudgetPlanner.Item("a", "Free", 0, "free", "learning", null, 0, 1, true),
                new SkillBudgetPlanner.Item("b", "Paid", 500, "once", "learning", "a", 6, 1, false)
        ), LocalDate.now());
        assertThat(free.total()).isZero();
        assertThat(free.lines()).extracting(SkillBudgetPlanner.Line::id).containsExactly("a");
    }

    @Test
    void skillBudgetIsAWeeklyPathForOneNewsIdea() {
        List<SkillBudgetPlanner.Item> catalog = List.of(
                new SkillBudgetPlanner.Item("vscode", "VS Code", 0, "free", "tools", null, 0, 5, true),
                new SkillBudgetPlanner.Item("notion-free", "Notion free", 0, "free", "tools", null, 0, 4, true),
                new SkillBudgetPlanner.Item("sheets", "Google Sheets", 0, "free", "tools", null, 0, 5, true),
                new SkillBudgetPlanner.Item("yt-python", "Python for beginners", 0, "free", "learning", null, 0, 5, true),
                new SkillBudgetPlanner.Item("swayam", "SWAYAM starter course", 0, "free", "learning", null, 0, 4, true),
                new SkillBudgetPlanner.Item("meetup", "Local founder meetup", 0, "free", "community", null, 0, 3, true),
                new SkillBudgetPlanner.Item("paid", "Structured Python course", 1200, "once", "learning", "yt-python", 6, 3, false),
                new SkillBudgetPlanner.Item("domain", "Domain name", 800, "once", "proof", null, 6, 4, false)
        );
        Map<String, List<String>> skills = Map.of(
                "vscode", List.of("code"),
                "notion-free", List.of("notes"),
                "sheets", List.of("sheets"),
                "yt-python", List.of("python"),
                "swayam", List.of("learning"),
                "meetup", List.of("network"),
                "paid", List.of("python"),
                "domain", List.of("site")
        );
        SkillBudgetPlanner.Path freePath = SkillBudgetPlanner.path(
                "Python tutoring for IT",
                "Colleges add Python labs",
                "Python tutoring for IT colleges add python labs",
                "learn",
                0,
                10,
                false,
                catalog,
                skills,
                Set.of(),
                LocalDate.now());
        assertThat(freePath.budget().total()).isZero();
        assertThat(freePath.budget().lines()).hasSizeLessThanOrEqualTo(4);
        assertThat(freePath.budget().lines()).extracting(SkillBudgetPlanner.Line::id)
                .contains("yt-python", "meetup", "vscode")
                .doesNotContain("paid", "domain", "swayam", "sheets", "notion-free");
        assertThat(freePath.weeks()).hasSize(12);
        assertThat(freePath.weeks()).allSatisfy(week -> {
            assertThat(week.goal()).contains("Python tutoring for IT");
            assertThat(week.toolName()).isNotBlank();
            assertThat(week.tasks()).hasSize(3);
            assertThat(week.taskType()).isNotBlank();
            assertThat(week.sittings()).isGreaterThanOrEqualTo(1);
            assertThat(week.baseHours()).isGreaterThan(0);
            assertThat(week.phase()).isNotBlank();
        });
        assertThat(freePath.weeks().get(0).toolName()).isEqualTo("Python for beginners");
        assertThat(freePath.weeks().get(0).taskType()).isEqualTo("learning");
        assertThat(freePath.weeks().stream().filter(w -> "build".equals(w.phase())).findFirst())
                .get()
                .extracting(SkillBudgetPlanner.WeekStep::toolName)
                .isEqualTo("VS Code");

        String sebiNews = "Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta Pandey";
        String sebiIdea = "Online course on Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta";
        assertThat(SkillBudgetPlanner.shortLabel(sebiIdea, sebiNews))
                .isEqualTo("online course on Sebi’s CAS guidelines likely within a week");
        SkillBudgetPlanner.Path sebiPath = SkillBudgetPlanner.path(
                sebiIdea,
                sebiNews,
                sebiIdea + " " + sebiNews,
                "learn",
                0,
                10,
                false,
                catalog,
                skills,
                Set.of(),
                LocalDate.now());
        assertThat(sebiPath.weeks().get(0).goal()).isEqualTo(
                "Write the offer for online course on Sebi’s CAS guidelines likely within a week");
        assertThat(sebiPath.weeks().get(0).goal()).doesNotContain("from “");
        assertThat(sebiPath.goal()).doesNotContain("from the news");
        assertThat(sebiPath.budget().lines()).extracting(SkillBudgetPlanner.Line::id)
                .contains("notion-free")
                .doesNotContain("vscode");
        assertThat(sebiPath.weeks().stream().filter(w -> "build".equals(w.phase())).map(SkillBudgetPlanner.WeekStep::toolName))
                .isNotEmpty()
                .allMatch(name -> !"VS Code".equals(name));
        assertThat(sebiPath.weeks().stream().filter(w -> "build".equals(w.phase())).findFirst())
                .get()
                .extracting(SkillBudgetPlanner.WeekStep::toolName)
                .isEqualTo("Notion free");

        SkillBudgetPlanner.Path paidPath = SkillBudgetPlanner.path(
                "Python tutoring for IT",
                "Colleges add Python labs",
                "Python tutoring for IT colleges add python labs",
                "learn",
                4000,
                10,
                false,
                catalog,
                skills,
                Set.of(),
                LocalDate.now());
        assertThat(paidPath.budget().total()).isLessThanOrEqualTo(4000);
        assertThat(paidPath.budget().lines()).extracting(SkillBudgetPlanner.Line::id)
                .contains("paid")
                .doesNotContain("yt-python");
    }

    @Test
    void weeksRespectHoursAndOutlineHasSections() {
        assertThat(WeeklyPlanner.horizon(3)).isEqualTo(8);
        assertThat(WeeklyPlanner.build(10, false)).allSatisfy(week -> {
            assertThat(week.tasks()).hasSize(3);
            assertThat(week.metric()).isNotBlank();
        });
        assertThat(WeeklyPlanner.shouldLighten(2)).isTrue();
        assertThat(WeeklyPlanner.build(10, true).get(0).tasks()).hasSize(2);
        Map<String, Object> outline = OutlineDraft.template("Pilot", 1200, 40);
        assertThat(outline).containsKeys("problem", "customer", "offer", "pricing", "channels", "monthlyCost", "breakEvenCustomers", "milestones90", "risks");
        assertThat(outline.get("monthlyCostSource")).isEqualTo("plan");
    }

    @Test
    void briefAndClaimRequireCitations() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(CitedBriefs.validate(mapper.readTree("{\"summary\":\"x\",\"agreements\":[{\"text\":\"a\"}]}"))).isEqualTo("citation");
        assertThat(CitedBriefs.validate(mapper.readTree("{\"summary\":\"x\",\"agreements\":[{\"text\":\"a\",\"citations\":[1]}]}"))).isNull();
        assertThat(ClaimChecks.validate(mapper.readTree("{\"stance\":\"mixed\",\"evidence\":[{\"text\":\"a\"}]}"))).isEqualTo("citation");
        assertThat(LanguageDetector.detect("समाचार")).isEqualTo("hi");
        assertThat(LanguageDetector.detect("news")).isEqualTo("en");
        assertThat(LlmOutputs.clampDelta(40)).isEqualTo(15);
        assertThat(LlmOutputs.truncate("abcdef", 3)).isEqualTo("abc");
    }

    @Test
    void digestReferralSpendAndPdf() {
        assertThat(DigestDeduper.unseen(Set.of(DigestDeduper.urlHash("https://a")), List.of("https://a", "https://b"))).containsExactly("https://b");
        assertThat(ReferralCodes.bonusFor(1, 0)).isZero();
        assertThat(ReferralCodes.bonusFor(0, 20)).isZero();
        assertThat(ReferralCodes.bonusFor(0, 1)).isEqualTo(2);
        assertThat(SpendCap.allowed(16, 15)).isFalse();
        assertThat(SpendCap.inr(1_000_000, 0, 10, 30)).isEqualTo(10);
        assertThat(new String(SimplePdf.fromText("Hello (world)"))).contains("%PDF");
    }

    @Test
    void sourcePackWeightChangesScore() {
        PointersService pointers = new PointersService();
        SourcePackCatalog catalog = new SourcePackCatalog();
        pointers.setPacks(catalog);
        Map<String, Object> page = Map.of(
                "url", "https://arxiv.org/abs/1",
                "title", "chip research",
                "description", "study",
                "snippet", "study",
                "text", "study"
        );
        catalog.replace(Map.of("arxiv.org", 1.0));
        int low = ((Number) pointers.score(page, List.of("IT"), "chip research study", "chip", 0).get("score")).intValue();
        catalog.replace(Map.of("arxiv.org", 2.0));
        int high = ((Number) pointers.score(page, List.of("IT"), "chip research study", "chip", 0).get("score")).intValue();
        assertThat(high).isGreaterThan(low);
    }

    @Test
    void hybridFusionKeepsRelevantPagesAndIndustryModesTag() {
        List<Map<String, Object>> pages = List.of(
                Map.of("url", "https://pinterest.com/a", "score", 90, "title", "cakes", "text", "cakes", "ageDays", 400),
                Map.of("url", "https://arxiv.org/abs/1", "score", 40, "title", "India semiconductor research study", "text", "India semiconductor research study dataset", "ageDays", 0)
        );
        List<Map<String, Object>> fused = HybridRanker.fuse(pages, "India semiconductor research", 45);
        assertThat(fused.get(0).get("url")).isEqualTo("https://arxiv.org/abs/1");
        assertThat(fused.get(0)).containsKey("trust");
        Map<String, Object> mode = IndustryModes.build("Medical", List.of(Map.of("title", "WHO guideline", "url", "https://who.int/g", "description", "guideline")));
        assertThat(mode.get("disclaimer")).asString().contains("diagnosis");
        assertThat(EvalMetrics.ndcgAt(List.of("a"), EvalMetrics.setOf(List.of("a")), 5)).isEqualTo(1.0);
    }

    @Test
    void signalFallbackKeepsIndustry() {
        Map<String, Object> signal = SignalSchemas.fallback("SEBI circular for brokers", "Share Market");
        assertThat(signal.get("event_type")).isEqualTo("regulation");
        assertThat(signal.get("industry")).isEqualTo("Share Market");
        assertThat(signal.get("magnitude")).isEqualTo(4);
    }
}
