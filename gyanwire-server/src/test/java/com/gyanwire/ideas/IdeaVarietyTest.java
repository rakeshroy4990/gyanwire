package com.gyanwire.ideas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdeaVarietyTest {

    @Test
    void formatCountFollowsTheNewsInsteadOfAlwaysEight() {
        List<String> regulation = IdeaVariety.formatsFor(
                "regulation",
                List.of("Compliance checklist, audit, or training"),
                "SEBI circular requires new brokers to finish compliance training before the deadline",
                82);
        List<String> price = IdeaVariety.formatsFor(
                "price_move",
                List.of("Substitute sourcing or a cost tracker"),
                "Diesel prices jumped this week for fleet owners",
                48);
        List<String> thin = IdeaVariety.formatsFor("other", List.of(), "A quiet note about a local fair", 30);

        assertThat(regulation).isNotEqualTo(price);
        assertThat(regulation).hasSizeLessThan(IdeaVariety.OPTIONS.size());
        assertThat(price).hasSizeLessThan(regulation.size());
        assertThat(thin).hasSizeLessThan(price.size());
        assertThat(regulation).contains("briefing", "checklist", "template");
        assertThat(price).contains("briefing", "tracker", "alert").doesNotContain("course");
        assertThat(thin).hasSize(2);
        assertThat(regulation).allMatch(IdeaVariety.OPTIONS::contains);
    }

    @Test
    void siblingHeadlinesDoNotShareOneCount() {
        int fundedLaunch = IdeaVariety.formatsFor(
                "tech_release",
                List.of("Integration service, template, or training"),
                "Google, Blackstone launch $5 billion AI cloud company to challenge Nvidia",
                35).size();
        int softwareRelease = IdeaVariety.formatsFor(
                "tech_release",
                List.of("Integration service, template, or training"),
                "Nvidia releases AI model that can run on single GPU on a laptop or desktop",
                33).size();
        int explainer = IdeaVariety.formatsFor(
                "other",
                List.of("Compliance checklist, audit, or training"),
                "What is OpenAI and Broadcom's Jalapeño AI chip, and why does it matter?",
                22).size();
        int taxMargin = IdeaVariety.formatsFor(
                "other",
                List.of(),
                "Windfall tax: $12 margin hit for Reliance",
                17).size();
        int howTo = IdeaVariety.formatsFor(
                "other",
                List.of(),
                "How to play Resident Evil Requiem early on PS5",
                21).size();

        assertThat(new java.util.HashSet<>(List.of(fundedLaunch, softwareRelease, explainer, taxMargin, howTo)).size())
                .isGreaterThan(1);
        assertThat(fundedLaunch).isGreaterThan(explainer);
        assertThat(howTo).isLessThan(taxMargin);
    }

    @Test
    void diversifyStaysInsideTheFormatsThisNewsSupports() {
        List<String> formats = List.of("briefing", "tracker", "alert");
        List<Map<String, Object>> varied = IdeaVariety.diversify(
                new ArrayList<>(List.of(
                        idea("Online Course on Fuel Tax", "A recorded online course"),
                        idea("Another online course", "A second course")
                )),
                "Diesel prices jumped",
                "IT",
                formats);

        assertThat(varied).hasSize(formats.size());
        assertThat(varied.stream().map(row -> String.valueOf(row.get("format"))))
                .containsExactlyInAnyOrder("Briefing", "Tracker", "Alerts");
        assertThat(courseTitles(varied)).isEmpty();
    }

    @Test
    void templatePackExplainsTheDownloadBusiness() {
        List<Map<String, Object>> varied = IdeaVariety.diversify(
                new ArrayList<>(),
                "GST filing dates moved up",
                "IT",
                List.of("template", "briefing"));

        Map<String, Object> pack = varied.stream()
                .filter(row -> "Template pack".equals(row.get("format")))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(pack.get("offer"))).contains("one-time download");
        assertThat(String.valueOf(pack.get("business_model"))).isEqualTo("One-time download");
    }

    @Test
    void onlineCourseIsOneOptionAmongDistinctFormats() {
        List<Map<String, Object>> drafts = new ArrayList<>();
        drafts.add(idea("Online Course on Fuel Tax Regulations", "A recorded online course for fleet owners"));
        drafts.add(idea("Online Course on Fuel Tax Regulations for accountants", "Another online course on the same rules"));
        drafts.add(idea("Fuel tax video course", "Self-paced course covering the new filing dates"));

        List<Map<String, Object>> varied = IdeaVariety.diversify(
                drafts, "Fuel tax rules change for transporters", "Share Market");

        assertThat(courseTitles(varied)).hasSize(1);
        assertThat(courseTitles(varied).get(0)).isEqualTo("Online Course on Fuel Tax Regulations");
        assertThat(varied).hasSize(IdeaVariety.OPTIONS.size());
        assertThat(varied.stream().map(row -> String.valueOf(row.get("format")))).containsExactlyInAnyOrder(
                "Online course", "Checklist", "Tracker", "Briefing", "Template pack", "Live session", "Setup help", "Alerts");
        assertThat(varied.stream().map(row -> String.valueOf(row.get("title"))).distinct()).hasSize(varied.size());
        assertThat(varied).allSatisfy(row -> {
            String text = row.get("title") + " " + row.get("offer") + " " + row.get("whyNow");
            assertThat(FitFilter.decide("Share Market", "working", text, 0, 25000, true, 4, 10, false).keep()).isTrue();
        });
    }

    @Test
    void aSingleCourseIsFilledOutToEveryCard() {
        List<Map<String, Object>> once = IdeaVariety.diversify(
                new ArrayList<>(List.of(idea("Online Course on Fuel Tax Regulations", "Recorded lessons"))),
                "Fuel tax rules change for transporters",
                "IT");

        assertThat(once).hasSize(IdeaVariety.OPTIONS.size());
        assertThat(courseTitles(once)).hasSize(1);
        assertThat(once.stream().filter(row -> Boolean.TRUE.equals(row.get("_formatRewritten")))).isNotEmpty();

        List<String> titles = once.stream().map(row -> String.valueOf(row.get("title"))).toList();
        List<Map<String, Object>> twice = IdeaVariety.diversify(once, "Fuel tax rules change for transporters", "IT");
        assertThat(twice.stream().map(row -> String.valueOf(row.get("title")))).containsExactlyElementsOf(titles);
        assertThat(twice.stream().filter(row -> Boolean.TRUE.equals(row.get("_formatRewritten")))).isEmpty();
    }

    @Test
    void alreadyVariedIdeasStayAsWritten() {
        List<Map<String, Object>> drafts = new ArrayList<>(List.of(
                idea("Fuel tax checklist for fleet owners", "A one-page checklist"),
                idea("Fuel tax tracker", "A tracker tool for filing dates"),
                idea("Fuel tax briefing", "A short explainer briefing")
        ));
        List<String> before = drafts.stream().map(row -> String.valueOf(row.get("title"))).toList();

        List<Map<String, Object>> varied = IdeaVariety.diversify(drafts, "Fuel tax rules change", "IT");

        assertThat(varied.stream().map(row -> String.valueOf(row.get("title"))).limit(before.size()))
                .containsExactlyElementsOf(before);
        assertThat(varied.subList(0, before.size()).stream().filter(row -> Boolean.TRUE.equals(row.get("_formatRewritten"))))
                .isEmpty();
        assertThat(varied.stream().map(row -> String.valueOf(row.get("format")))).contains(
                "Online course", "Checklist", "Tracker", "Briefing", "Template pack", "Live session", "Setup help", "Alerts");
    }

    private static List<String> courseTitles(List<Map<String, Object>> ideas) {
        return ideas.stream()
                .map(row -> String.valueOf(row.get("title")))
                .filter(title -> title.toLowerCase(Locale.ROOT).contains("course"))
                .toList();
    }

    private static Map<String, Object> idea(String title, String offer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("offer", offer);
        row.put("whyNow", "The fuel tax circular takes effect this month.");
        row.put("capitalNeededInr", 0);
        row.put("hoursPerWeek", 4);
        return row;
    }
}
