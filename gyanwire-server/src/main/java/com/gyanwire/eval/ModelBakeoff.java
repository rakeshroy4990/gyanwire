package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.llm.ModelPrices;
import com.gyanwire.plans.OutlineDraft;
import com.gyanwire.research.engine.LlmService;

import java.util.Map;

/**
 * Same idea and outline prompts on two pinned models. Scores prompt compliance
 * and quality per rupee. Does not change temperature or response_format.
 */
public final class ModelBakeoff {

    private ModelBakeoff() {
    }

    public static ObjectNode compare(LlmClient low, LlmClient high, ObjectMapper mapper) throws Exception {
        Map<String, Object> outlineTemplate = OutlineDraft.template("UPI merchant checklist", 2400, 62);
        String ideaSystem = LlmService.prompt("idea.v1.txt");
        String outlineSystem = LlmService.prompt("outline.v1.txt");
        String ideaUser = mapper.writeValueAsString(Map.of(
                "language", "en",
                "news", Map.of(
                        "title", "India extends the UPI subsidy for small merchants through March",
                        "description", "The government said the zero-fee window for UPI merchant transactions under ₹2,000 continues until 31 March.",
                        "industry", "IT"
                ),
                "signal", Map.of("event_type", "govt_scheme", "magnitude", 3),
                "profile", Map.of(
                        "persona", "working",
                        "skills", java.util.List.of("writing"),
                        "hoursPerWeek", 6,
                        "location", "Pune"
                ),
                "patterns", java.util.List.of(Map.of(
                        "id", "P6",
                        "name", "Consumer shift",
                        "ideaShape", "A small service"
                ))
        ));
        String outlineUser = mapper.writeValueAsString(outlineTemplate);

        ObjectNode report = mapper.createObjectNode();
        report.put("date", java.time.LocalDate.now().toString());
        report.put("usdToInr", ModelPrices.USD_TO_INR);
        report.put("temperature", 0.2);
        report.put("responseFormat", "json_object");
        ObjectNode tasks = report.putObject("tasks");
        ObjectNode idea = tasks.putObject("idea");
        idea.set("low", run(low, "idea", "idea.v1", ideaSystem, ideaUser, node -> IdeaOutlineScore.ideas(node), mapper));
        idea.set("high", run(high, "idea", "idea.v1", ideaSystem, ideaUser, node -> IdeaOutlineScore.ideas(node), mapper));
        ObjectNode outline = tasks.putObject("outline");
        outline.set("low", run(low, "outline", "outline.v1", outlineSystem, outlineUser,
                node -> IdeaOutlineScore.outline(node, outlineTemplate), mapper));
        outline.set("high", run(high, "outline", "outline.v1", outlineSystem, outlineUser,
                node -> IdeaOutlineScore.outline(node, outlineTemplate), mapper));

        boolean useFlagship = useFlagship(idea, outline);
        report.put("qualityFloor", QUALITY_FLOOR);
        report.put("useFlagshipForHighFeatures", useFlagship);
        report.put("decision", useFlagship
                ? "The high model clears the quality floor on a task the low model misses, without lowering the other task. Pro and Team can set LLM_MODEL_HIGH to that model for idea and outline."
                : "Keep idea and outline on gpt-4o-mini. The high model did not clear the quality floor on a task the low model missed, and its quality per rupee is lower. LLM_MODEL stays the rollback value.");
        return report;
    }

    static final double QUALITY_FLOOR = 0.8;

    static boolean useFlagship(ObjectNode idea, ObjectNode outline) {
        if (!idea.path("high").path("ok").asBoolean(false) || !outline.path("high").path("ok").asBoolean(false)) {
            return false;
        }
        double ideaLow = idea.path("low").path("quality").asDouble(0);
        double ideaHigh = idea.path("high").path("quality").asDouble(0);
        double outlineLow = outline.path("low").path("quality").asDouble(0);
        double outlineHigh = outline.path("high").path("quality").asDouble(0);
        if (ideaHigh < ideaLow || outlineHigh < outlineLow) {
            return false;
        }
        boolean liftsIdea = ideaLow < QUALITY_FLOOR && ideaHigh >= QUALITY_FLOOR;
        boolean liftsOutline = outlineLow < QUALITY_FLOOR && outlineHigh >= QUALITY_FLOOR;
        return liftsIdea || liftsOutline;
    }

    private static ObjectNode run(
            LlmClient client,
            String feature,
            String version,
            String system,
            String user,
            java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, Double> score,
            ObjectMapper mapper
    ) {
        LlmClient.CallResult call = client.completeCall(null, feature, version, system, user);
        double quality = call.ok() ? score.apply(call.parsed()) : 0;
        Double costInr = ModelPrices.inr(call.model(), call.tokensIn(), call.tokensOut());
        Double perRupee = ModelPrices.qualityPerRupee(quality, costInr);
        ObjectNode row = mapper.createObjectNode();
        row.put("model", call.model());
        row.put("ok", call.ok());
        row.put("status", call.status());
        row.put("quality", round(quality));
        if (call.tokensIn() != null) {
            row.put("tokensIn", call.tokensIn());
        }
        if (call.tokensOut() != null) {
            row.put("tokensOut", call.tokensOut());
        }
        row.put("latencyMs", call.latencyMs());
        if (costInr != null) {
            row.put("costInr", round(costInr));
        }
        if (perRupee != null) {
            row.put("qualityPerRupee", perRupee);
        }
        if (call.error() != null) {
            row.put("error", call.error());
        }
        return row;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
