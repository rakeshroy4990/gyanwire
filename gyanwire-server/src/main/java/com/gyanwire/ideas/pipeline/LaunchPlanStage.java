package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;

public final class LaunchPlanStage {

    private final ObjectMapper mapper;

    public LaunchPlanStage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> run(IdeaStageClient client, UUID userId, BusinessIdea idea, String newsTitle) {
        if (SocialMediaPlan.isSocial(idea)) {
            return SocialMediaPlan.fromIdea(idea, newsTitle);
        }
        Map<String, Object> fallback = fromIdea(idea, newsTitle);
        if (client == null || idea == null) {
            return fallback;
        }
        String user;
        try {
            user = mapper.writeValueAsString(Map.of("newsTitle", newsTitle == null ? "" : newsTitle, "idea", idea));
        } catch (Exception ex) {
            return fallback;
        }
        JsonNode node = StageCalls.completeValidated(
                client,
                userId,
                "idea-launch-plan.v1",
                "idea-launch",
                LlmService.prompt("idea-launch-plan.v1.txt"),
                user,
                ModelTier.MAIN,
                LaunchPlanStage::validate,
                2);
        Map<String, Object> parsed = parse(node);
        return parsed == null ? fallback : parsed;
    }

    public static String validate(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        if (text(node, "customer").length() < 8 || text(node, "offer").length() < 8) {
            return "customer or offer";
        }
        if (!node.path("schedule").isArray() || node.path("schedule").size() != 7) {
            return "schedule";
        }
        JsonNode copy = node.get("landingCopy");
        if (copy == null || text(copy, "headline").isBlank() || text(copy, "subhead").isBlank() || text(copy, "cta").isBlank()) {
            return "landingCopy";
        }
        if (!node.path("outreach").isArray() || node.path("outreach").size() != 3) {
            return "outreach";
        }
        if (text(node, "validationMetric").length() < 8) {
            return "validationMetric";
        }
        return null;
    }

    public static Map<String, Object> fromIdea(BusinessIdea idea, String newsTitle) {
        if (SocialMediaPlan.isSocial(idea)) {
            return SocialMediaPlan.fromIdea(idea, newsTitle);
        }
        if (idea == null) {
            return Map.of();
        }
        List<String> week = idea.firstWeek() == null ? List.of() : idea.firstWeek();
        List<Map<String, String>> schedule = new ArrayList<>();
        String[] days = {"Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7"};
        for (int i = 0; i < days.length; i++) {
            String task = i < week.size() ? week.get(i) : (i == 6
                    ? "Count the validation metric and decide whether to continue."
                    : "Talk to one person in the customer group and write down their words.");
            schedule.add(Map.of("day", days[i], "task", task));
        }
        String who = idea.customer() == null ? "" : idea.customer().who();
        String price = idea.revenueModel() == null || idea.revenueModel().price() == null
                ? ""
                : idea.revenueModel().price();
        String metric = idea.validationTest() == null ? "" : idea.validationTest().successMetric();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("customer", who);
        plan.put("offer", idea.oneLiner());
        plan.put("schedule", schedule);
        plan.put("landingCopy", Map.of(
                "headline", idea.title(),
                "subhead", idea.whyTheyPay() == null ? idea.oneLiner() : idea.whyTheyPay(),
                "cta", price.isBlank() ? "Get the first version" : "See the offer (" + price + ")"));
        plan.put("outreach", List.of(
                "I saw " + (newsTitle == null || newsTitle.isBlank() ? "this news" : newsTitle)
                        + " and made a one-page offer for " + who + ". Want the link?",
                "Would you pay " + (price.isBlank() ? "for this" : price) + " if it solved this before the window closes?",
                "I am testing this for 7 days. The pass mark is: " + metric));
        plan.put("validationMetric", metric);
        return plan;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(JsonNode node) {
        if (validate(node) != null) {
            return null;
        }
        try {
            return mapper.convertValue(node, Map.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return IdeaPipelineSchemas.text(node, field);
    }
}
