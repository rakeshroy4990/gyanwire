package com.gyanwire.actionplan.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.ActionPlanPipeline;
import com.gyanwire.actionplan.ActionPlanSink;
import com.gyanwire.actionplan.ActionPlanStore;
import com.gyanwire.actionplan.StepDetailService;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActionPlanController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ActionPlanControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockBean
    ActionPlanPipeline pipeline;
    @MockBean
    ActionPlanStore store;
    @MockBean
    StepDetailService stepDetails;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void createStreamsSkeletonPlanBeforeFinishedPlan() throws Exception {
        AtomicBoolean finishedEmitted = new AtomicBoolean(false);
        List<String> planStatusesAtEmit = new ArrayList<>();

        doAnswer(invocation -> {
            ActionPlanSink sink = invocation.getArgument(12);
            Map<String, Object> skeleton = Map.of(
                    "planId", "plan-1",
                    "kind", "action_plan",
                    "format", "short_vertical",
                    "buildStatus", "building",
                    "steps", List.of(Map.of("id", "s1")));
            sink.plan(skeleton);
            planStatusesAtEmit.add("building");
            sink.status("brief");
            Map<String, Object> ready = Map.of(
                    "planId", "plan-1",
                    "kind", "action_plan",
                    "format", "short_vertical",
                    "buildStatus", "ready",
                    "steps", List.of(Map.of("id", "s1"), Map.of("id", "s2")));
            sink.plan(ready);
            finishedEmitted.set(true);
            planStatusesAtEmit.add("ready");
            return null;
        }).when(pipeline).generateStream(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any());

        MvcResult result = mvc.perform(post("/api/action-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.parseMediaType("application/x-ndjson"))
                        .content("{\"newsUrl\":\"https://example.com/a\",\"title\":\"Haiku 5.5\",\"budgetMinutes\":240}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn();

        assertThat(finishedEmitted.get()).isTrue();
        assertThat(planStatusesAtEmit).containsExactly("building", "ready");

        String[] lines = result.getResponse().getContentAsString().trim().split("\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(4);
        JsonNode open = mapper.readTree(lines[0]);
        assertThat(open.get("type").asText()).isEqualTo("open");
        assertThat(open.get("intent").asText()).isEqualTo("action_plan");

        JsonNode firstPlan = mapper.readTree(lines[1]);
        assertThat(firstPlan.get("type").asText()).isEqualTo("plan");
        assertThat(firstPlan.get("plan").get("planId").asText()).isEqualTo("plan-1");
        assertThat(firstPlan.get("plan").get("buildStatus").asText()).isEqualTo("building");

        JsonNode done = mapper.readTree(lines[lines.length - 1]);
        assertThat(done.get("type").asText()).isEqualTo("done");
        assertThat(done.get("success").asBoolean()).isTrue();
    }

    @Test
    void createRequiresNewsUrl() throws Exception {
        mvc.perform(post("/api/action-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Haiku 5.5\"}"))
                .andExpect(status().isBadRequest());
    }
}
