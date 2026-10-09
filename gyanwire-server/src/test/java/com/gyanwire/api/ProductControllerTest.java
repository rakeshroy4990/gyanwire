package com.gyanwire.api;

import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import com.gyanwire.ideas.IdeaService;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.plans.LearningPlanService;
import com.gyanwire.profile.ProfileService;
import com.gyanwire.projects.ProjectService;
import com.gyanwire.referrals.ReferralService;
import com.gyanwire.sources.SourcePackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void referralRequiresACode() throws Exception {
        mvc.perform(post("/api/referrals/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ab\"}"))
                .andExpect(status().isBadRequest());
    }
}
