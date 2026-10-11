package com.gyanwire.news;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import com.gyanwire.controller.dto.response.ResearchIndustryResponse;
import com.gyanwire.service.ResearchIndustryService;
import com.gyanwire.usage.UsageService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NewsController.class, NewsAdminController.class}, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class NewsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    NewsQueryService news;

    @MockBean
    NewsStore store;

    @MockBean
    ResearchIndustryService industries;

    @MockBean
    NewsFetchCoordinator coordinator;

    @MockBean
    UsageService usage;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultNewsStreamsNdjson() throws Exception {
        when(news.defaults(eq(5), isNull())).thenReturn(Map.of(
                "results", List.of(
                        Map.of("title", "First launch", "url", "https://a.example/1", "opportunityScore", 80),
                        Map.of("title", "Second launch", "url", "https://a.example/2", "opportunityScore", 70)
                ),
                "freshness", "last 7 days",
                "requested", 5,
                "returned", 2,
                "label", "Top searched product news",
                "message", "Two signals",
                "isDefaultNews", true,
                "category", "Default"
        ));

        String body = mvc.perform(get("/api/news/default")
                        .param("limit", "5")
                        .param("intent", "products")
                        .accept(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String[] lines = body.split("\n");
        assertThat(lines).hasSize(5);
        assertThat(lines[0]).contains("\"type\":\"open\"");
        assertThat(lines[1]).contains("\"type\":\"meta\"");
        assertThat(lines[1]).doesNotContain("\"results\"");
        assertThat(lines[2]).contains("\"type\":\"item\"").contains("First launch");
        assertThat(lines[3]).contains("\"type\":\"item\"").contains("Second launch");
        assertThat(lines[4]).contains("\"type\":\"done\"").contains("\"returned\":2");
    }

    @Test
    void unknownIndustryIsRejected() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        mvc.perform(get("/api/news/NotARealIndustry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_INDUSTRY"));
    }

    @Test
    void emptyIndustryReportsWarmingUp() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), eq(5), eq("products"), eq(14), isNull()))
                .thenReturn(Map.of(
                        "results", List.of(),
                        "freshness", "warming_up",
                        "requested", 5,
                        "returned", 0,
                        "label", "Medical products"
                ));
        mvc.perform(get("/api/news/Medical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freshness").value("warming_up"))
                .andExpect(jsonPath("$.data.returned").value(0));
    }

    @Test
    void widenedWindowIsLabelled() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), eq("Paediatrics"), eq(5), eq("products"), eq(14), isNull()))
                .thenReturn(Map.of(
                        "results", List.of(Map.of("title", "A launch", "opportunityScore", 70)),
                        "freshness", "last 30 days",
                        "requested", 5,
                        "returned", 1,
                        "label", "Paediatrics products"
                ));
        mvc.perform(get("/api/news/Medical").param("sub", "Paediatrics").param("intent", "products").param("window", "14d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freshness").value("last 30 days"))
                .andExpect(jsonPath("$.data.results[0].opportunityScore").value(70));
    }

    @Test
    void unknownCountryIsRejected() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        mvc.perform(get("/api/news/Medical").param("country", "ZZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_COUNTRY"));
    }

    @Test
    void countryFilterIsApplied() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), eq(5), eq("products"), eq(14), eq("US")))
                .thenReturn(Map.of(
                        "results", List.of(),
                        "country", "US",
                        "regionPreference", "Worldwide, India prioritized"
                ));
        mvc.perform(get("/api/news/Medical").param("country", "us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.country").value("US"))
                .andExpect(jsonPath("$.data.regionPreference").value("Worldwide, India prioritized"));
    }

    @Test
    void ingestIsAdminOnlyOutsideDev() throws Exception {
        mvc.perform(post("/api/admin/news/ingest").param("industry", "Medical"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanIngest() throws Exception {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(id, "admin@gyanwire.com", "admin"), null)
        );
        when(usage.isAdmin(id)).thenReturn(true);
        when(coordinator.fetch(eq("Medical"), isNull(), eq(NewsFetchCoordinator.Mode.MANUAL), any()))
                .thenReturn(IndustryFetchGate.Outcome.done());
        mvc.perform(post("/api/admin/news/ingest").param("industry", "Medical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("Medical"));
    }

    private static ResearchIndustryResponse catalog() {
        return new ResearchIndustryResponse(UUID.randomUUID(), "Medical", "Medical products", List.of("Paediatrics"));
    }
}
