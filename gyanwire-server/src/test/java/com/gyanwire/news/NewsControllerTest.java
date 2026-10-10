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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void unknownIndustryIsRejected() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        mvc.perform(get("/api/news/NotARealIndustry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_INDUSTRY"));
    }

    @Test
    void emptyIndustryReportsWarmingUp() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), eq(5), eq("products"), eq(14)))
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
        when(news.industry(eq("Medical"), eq("Paediatrics"), eq(5), eq("products"), eq(14)))
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
