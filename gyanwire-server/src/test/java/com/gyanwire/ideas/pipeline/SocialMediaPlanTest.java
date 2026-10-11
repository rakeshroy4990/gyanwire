package com.gyanwire.ideas.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialMediaPlanTest {

    private static final Set<String> REPOS = Set.of(
            "kmwallio/ThiefMD",
            "chuangcaleb/obsidian-fountain-editor",
            "mltframework/shotcut",
            "KDE/kdenlive",
            "OHF-Voice/piper1-gpl",
            "Elleo/pied",
            "huginn/huginn");

    @Test
    void socialIdeaUsesHoursAndPlatforms() {
        var plan = LaunchPlanStage.fromIdea(idea(), "Labs told to file calibration logs");
        assertEquals("social", plan.get("kind"));
        assertEquals(1, plan.get("durationDays"));
        assertTrue(String.valueOf(plan.get("span")).contains("day 2"));
        assertFalse(plan.containsKey("schedule"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> platforms = (List<Map<String, Object>>) plan.get("platforms");
        assertEquals(List.of("youtube", "instagram", "linkedin"), platforms.stream().map(row -> row.get("id")).toList());
        for (Map<String, Object> platform : platforms) {
            assertHours(platform);
        }
    }

    @Test
    void otherIdeasStayOnASevenDayPlan() {
        var plan = LaunchPlanStage.fromIdea(idea("b2b_service"), "Labs told to file calibration logs");
        assertEquals(7, ((List<?>) plan.get("schedule")).size());
        assertFalse(plan.containsKey("platforms"));
    }

    @Test
    void linkedToolsRequireUseSummaries() {
        var plan = LaunchPlanStage.fromIdea(idea(), "Labs told to file calibration logs");
        assertTrue(SocialMediaPlan.hasLinkedTools(plan));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> platforms = (List<Map<String, Object>>) plan.get("platforms");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hours = (List<Map<String, Object>>) platforms.get(0).get("hours");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> paid = (List<Map<String, String>>) hours.get(0).get("paid");
        Map<String, String> chatgpt = paid.stream()
                .filter(row -> "ChatGPT".equals(row.get("name")))
                .findFirst()
                .orElseThrow();
        assertTrue(chatgpt.get("whyPaid").toLowerCase().contains("free"));
        assertTrue(chatgpt.get("whyPaid").toLowerCase().contains("plus")
                || chatgpt.get("whyPaid").toLowerCase().contains("gpt-4o"));

        Map<String, Object> stalePaid = Map.of("name", "ChatGPT", "url", "https://chatgpt.com", "note", "Paid plan.");
        Map<String, Object> staleHour = Map.of(
                "hour", "Hour 1",
                "openSource", List.of(Map.of(
                        "name", "ThiefMD",
                        "repo", "kmwallio/ThiefMD",
                        "url", "https://github.com/kmwallio/ThiefMD",
                        "why", "Write the script.")),
                "paid", List.of(stalePaid));
        Map<String, Object> stale = Map.of("platforms", List.of(Map.of("hours", List.of(staleHour))));
        assertFalse(SocialMediaPlan.hasLinkedTools(stale));
    }

    @SuppressWarnings("unchecked")
    private static void assertHours(Map<String, Object> platform) {
        List<Map<String, Object>> hours = (List<Map<String, Object>>) platform.get("hours");
        assertEquals(4, hours.size());
        assertEquals(List.of("script", "video", "audio", "automation"), hours.stream().map(row -> row.get("role")).toList());
        for (int i = 0; i < hours.size(); i++) {
            Map<String, Object> hour = hours.get(i);
            assertEquals("Hour " + (i + 1), hour.get("hour"));
            assertFalse(String.valueOf(hour.get("task")).isBlank());
            List<Map<String, Object>> open = (List<Map<String, Object>>) hour.get("openSource");
            List<Map<String, String>> paid = (List<Map<String, String>>) hour.get("paid");
            assertFalse(open.isEmpty());
            assertFalse(paid.isEmpty());
            for (Map<String, Object> tool : open) {
                assertTrue(REPOS.contains(String.valueOf(tool.get("repo"))));
                assertTrue(String.valueOf(tool.get("url")).startsWith("https://github.com/" + tool.get("repo")));
                assertFalse(String.valueOf(tool.get("summary")).isBlank());
            }
            for (Map<String, String> tool : paid) {
                assertTrue(String.valueOf(tool.get("url")).startsWith("https://"));
                assertFalse(String.valueOf(tool.get("summary")).isBlank());
                assertFalse(String.valueOf(tool.get("whyPaid")).isBlank());
            }
        }
    }

    private static IdeaModels.BusinessIdea idea() {
        return idea("comparison_content");
    }

    private static IdeaModels.BusinessIdea idea(String angle) {
        return new IdeaModels.BusinessIdea(
                angle,
                "Post the lab deadline before 1 November",
                "A short post that tells lab owners the filing date.",
                new IdeaModels.Customer("Owners of small diagnostic labs", "YouTube"),
                "The notice names a 1 November upload date.",
                "A missed date can pause testing for a day.",
                "The deadline is days away and the notice is published.",
                "Publish one short that names the date and the form. Pin the source link under it.",
                new IdeaModels.RevenueModel("ads", "", "Views can carry display ads on the video."),
                List.of("Draft the script today", "Cut the clip today", "Schedule the post today"),
                new IdeaModels.ValidationTest("Post the video", "500 views in 2 days"),
                List.of("The clip can sound like every other deadline post."),
                "hours",
                false);
    }
}
