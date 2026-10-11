package com.gyanwire.api;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import com.gyanwire.ideas.IdeaService;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.plans.LearningPlanService;
import com.gyanwire.profile.ProfileService;
import com.gyanwire.projects.ProjectService;
import com.gyanwire.referrals.ReferralService;
import com.gyanwire.sources.SourcePackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProfileService profiles;
    @MockBean
    IdeaService ideas;
    @MockBean
    LearningPlanService plans;
    @MockBean
    ProjectService projects;
    @MockBean
    SourcePackService packs;
    @MockBean
    ReferralService referrals;
    @MockBean
    FlowStore store;
    @MockBean
    McpService mcp;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileRejectsMissingConsent() throws Exception {
        mvc.perform(put("/api/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":false,\"persona\":\"student\",\"goal90d\":\"learn\",\"capitalBand\":\"0\",\"incomeBand\":\"0\",\"investPct\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ideaRequiresHttpUrl() throws Exception {
        mvc.perform(post("/api/ideas/from-news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"notaurl\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromNewsStreamsNdjson() throws Exception {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(id, "a@gyanwire.com", "user"), null)
        );
        when(ideas.fromNews(eq(id), any())).thenReturn(Map.of(
                "runId", "run-1",
                "cached", false,
                "pipeline", "idea-pipeline.v2",
                "gate", Map.of("proceed", true),
                "ideas", List.of(
                        Map.of("id", "1", "title", "First idea", "score", 80),
                        Map.of("id", "2", "title", "Second idea", "score", 70)
                )
        ));

        String body = mvc.perform(post("/api/ideas/from-news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.parseMediaType("application/x-ndjson"))
                        .content("{\"url\":\"https://news.example/a\",\"title\":\"A launch\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String[] lines = body.split("\n");
        assertThat(lines).hasSize(5);
        assertThat(lines[0]).contains("\"type\":\"open\"");
        assertThat(lines[1]).contains("\"type\":\"meta\"").contains("run-1").doesNotContain("\"ideas\"");
        assertThat(lines[2]).contains("\"type\":\"item\"").contains("First idea");
        assertThat(lines[3]).contains("\"type\":\"item\"").contains("Second idea");
        assertThat(lines[4]).contains("\"type\":\"done\"").contains("\"returned\":2");
    }

    @Test
    void referralRequiresACode() throws Exception {
        mvc.perform(post("/api/referrals/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ab\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void launchPlanRequiresSignIn() throws Exception {
        mvc.perform(post("/api/ideas/" + java.util.UUID.randomUUID() + "/launch-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
