package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.ActionPlanPipeline;
import com.gyanwire.ideas.IdeaSink;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.usage.UsageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpportunityPipelineStreamTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void socialItemIsEmittedBeforeAngleOrBuildClientRuns() throws Exception {
        FlowStore store = mock(FlowStore.class);
        UsageService usage = mock(UsageService.class);
        ActionPlanPipeline actionPlans = mock(ActionPlanPipeline.class);
        when(store.findIdeaRun(anyString())).thenReturn(null);

        AtomicBoolean socialEmitted = new AtomicBoolean();
        List<String> itemAngles = new ArrayList<>();
        List<String> clientFeatures = new ArrayList<>();
        IdeaSink sink = new IdeaSink() {
            @Override
            public void status(String stage) {
            }

            @Override
            public void meta(Map<String, Object> frame) {
            }

            @Override
            public void item(Map<String, Object> row) {
                String angle = String.valueOf(row.get("angleType"));
                itemAngles.add(angle);
                if ("comparison_content".equals(angle)) {
                    socialEmitted.set(true);
                }
            }
        };

        IdeaStageClient client = (userId, promptVersion, feature, system, user, tier) -> {
            clientFeatures.add(feature);
            assertThat(socialEmitted.get())
                    .as("social card must leave before %s", feature)
                    .isTrue();
            try {
                return jsonFor(feature);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };

        OpportunityPipeline pipeline = new OpportunityPipeline(store, usage, client, mapper, actionPlans);
        UUID userId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "url", "https://news.example/prepaid-fuel",
                "title", "HPCL launches a prepaid fuel card for truck fleets",
                "description", "Fleet owners can load the card today in India.",
                "industry", "AI"
        );

        Map<String, Object> result = pipeline.fromNews(
                userId,
                body,
                Map.of("persona", "working", "incomeBand", "0", "hoursPerWeek", 10),
                sink
        );

        assertThat(itemAngles.get(0)).isEqualTo("comparison_content");
        assertThat(itemAngles.stream().filter("comparison_content"::equals).count()).isEqualTo(1);
        assertThat(clientFeatures).contains("idea-signals", "idea-gate");
        assertThat(clientFeatures).doesNotContain("idea-angles", "idea-build");
        assertThat(result.get("ideas")).asList().hasSize(1);
        verify(store).insertIdeaRun(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(store, never()).deleteIdeasForRun(any(), any());
    }

    private JsonNode jsonFor(String feature) throws Exception {
        if ("idea-signals".equals(feature)) {
            return mapper.readTree("""
                    {
                      "headline": "HPCL launches a prepaid fuel card for truck fleets",
                      "eventType": "launch",
                      "entities": ["HPCL"],
                      "confirmedFacts": ["The card is live for fleet owners."],
                      "unconfirmedClaims": [],
                      "indiaRelevance": "high",
                      "timeWindow": { "startsAt": "", "urgency": "days" },
                      "affectedGroups": ["Fleet owners in India"]
                    }
                    """);
        }
        if ("idea-gate".equals(feature)) {
            return mapper.readTree("""
                    {
                      "proceed": false,
                      "reason": "This story is only a product announcement without a clear buyer moment.",
                      "confidence": 0.7,
                      "suggestions": ["Pick a story with a named buyer and deadline."]
                    }
                    """);
        }
        throw new IllegalStateException("Unexpected feature " + feature);
    }
}
