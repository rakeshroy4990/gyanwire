package com.gyanwire.actionplan.api;

import com.gyanwire.actionplan.ActionPlanPipeline;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActionPlanController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ActionPlanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ActionPlanPipeline pipeline;
    @MockBean
    ActionPlanStore store;
    @MockBean
    StepDetailService stepDetails;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void createReturnsPlan() throws Exception {
        when(pipeline.generate(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Map.of(
                        "kind", "action_plan",
                        "format", "short_vertical",
                        "budgetMinutes", 240,
                        "steps", List.of(Map.of("id", "s1"))));

        mvc.perform(post("/api/action-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsUrl\":\"https://example.com/a\",\"title\":\"Haiku 5.5\",\"budgetMinutes\":240}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.format").value("short_vertical"));
    }

    @Test
    void createRequiresNewsUrl() throws Exception {
        mvc.perform(post("/api/action-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Haiku 5.5\"}"))
                .andExpect(status().isBadRequest());
    }
}
