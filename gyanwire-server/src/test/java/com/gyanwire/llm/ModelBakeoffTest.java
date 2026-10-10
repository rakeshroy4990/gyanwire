package com.gyanwire.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.eval.ModelBakeoff;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelBakeoffTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sideBySideReportsQualityPerRupee() throws Exception {
        String strong = eightIdeasBody();
        String weak = "{\"ideas\":[{\"title\":\"Online course on UPI\"}]}";
        LlmClient low = client("gpt-4o-mini", weak, outlineBody(false), 100, 80);
        LlmClient high = client("gpt-5.4", strong, outlineBody(true), 400, 900);
        var report = ModelBakeoff.compare(low, high, mapper);

        assertThat(report.path("tasks").path("idea").path("high").path("quality").asDouble())
                .isGreaterThan(report.path("tasks").path("idea").path("low").path("quality").asDouble());
        assertThat(report.path("tasks").path("idea").path("low").path("qualityPerRupee").asDouble())
                .isPositive();
        assertThat(report.path("tasks").path("idea").path("high").path("costInr").asDouble())
                .isGreaterThan(report.path("tasks").path("idea").path("low").path("costInr").asDouble());
        assertThat(report.path("tasks").path("idea").path("low").path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(report.path("tasks").path("outline").path("high").path("model").asText()).isEqualTo("gpt-5.4");
        assertThat(report.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(report.path("responseFormat").asText()).isEqualTo("json_object");
        assertThat(report.path("useFlagshipForHighFeatures").asBoolean()).isTrue();
    }

    private LlmClient client(String model, String ideaJson, String outlineJson, int tokensIn, int tokensOut) {
        return new LlmClient(
                "sk-test",
                "https://api.openai.com/v1",
                model,
                mapper,
                null,
                (uri, jsonBody, apiKey) -> {
                    boolean outline = jsonBody.contains("monthlyCost");
                    String content = outline ? outlineJson : ideaJson;
                    String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
                    String body = "{\"usage\":{\"prompt_tokens\":" + tokensIn + ",\"completion_tokens\":" + tokensOut
                            + "},\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
                    return new LlmClient.LlmHttpResult(200, body);
                }
        );
    }

    private static String outlineBody(boolean rewrite) {
        String problem = rewrite
                ? "Merchants need a simpler way to keep the zero-fee window. Estimate."
                : "People affected by this news need a simpler way to act. Source: idea.";
        return "{\"problem\":\"" + problem + "\","
                + "\"customer\":\"The first customers match the profile skills and location. Source: profile.\","
                + "\"offer\":\"UPI merchant checklist\","
                + "\"pricing\":\"Estimate. Source: plan JSON. Start with a pilot price.\","
                + "\"channels\":\"Direct conversations, then a short public page.\","
                + "\"monthlyCost\":2400,\"monthlyCostSource\":\"plan\",\"breakEvenCustomers\":4,"
                + "\"milestones90\":\"Days 1-30 learn, 31-60 proof, 61-90 first commitment.\","
                + "\"risks\":\"Regulatory and demand risk. Score 62 is a model of fit, not a forecast.\","
                + "\"skills\":\"Skills still to build are the unpaid gaps in the skill plan.\"}";
    }

    private static String eightIdeasBody() {
        return "{\"ideas\":["
                + idea("Online course on UPI fees", "a short online course", "Course fee")
                + "," + idea("UPI fee checklist", "a one-page checklist", "One-time download")
                + "," + idea("UPI volume tracker", "a tracker tool", "Low monthly fee")
                + "," + idea("UPI subsidy briefing", "an explainer briefing", "Low monthly fee")
                + "," + idea("UPI notice template pack", "a template pack", "One-time download")
                + "," + idea("Live working session on UPI fees", "a live working session", "Seat fee")
                + "," + idea("Setup help for UPI signage", "done-for-you setup help", "Project fee")
                + "," + idea("UPI rule change alerts", "an alert when the rule changes", "Low monthly fee")
                + "]}";
    }

    private static String idea(String title, String offer, String model) {
        return "{\"title\":\"" + title + "\",\"segment\":\"shop owners\",\"pain\":\"fees\","
                + "\"offer\":\"" + offer + "\",\"business_model\":\"" + model + "\","
                + "\"why_now\":\"zero-fee window continues until 31 March\","
                + "\"first_customer_path\":\"shops\",\"capital_needed_inr\":0,\"hours_per_week\":4,"
                + "\"market_size\":0.5,\"competition_gap\":0.5,\"regulatory_risk\":0.2,\"confidence\":0.6}";
    }
}
