package com.gyanwire.news;

import com.gyanwire.actionplan.NewsIdeasService;
import com.gyanwire.actionplan.research.VideoResearchPack;
import com.gyanwire.actionplan.research.VideoResearchService;
import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import com.gyanwire.controller.dto.response.ResearchIndustryResponse;
import com.gyanwire.ideas.IdeaSink;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    NewsIdeasService newsIdeas;

    @MockBean
    VideoResearchService videoResearch;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultNewsStreamsNdjson() throws Exception {
        when(news.defaults(eq(10), isNull(), any(NewsViewer.class), eq(1))).thenReturn(Map.of(
                "results", List.of(
                        Map.of("title", "First launch", "url", "https://a.example/1", "opportunityScore", 80),
                        Map.of("title", "Second launch", "url", "https://a.example/2", "opportunityScore", 70)
                ),
                "freshness", "last 30 days",
                "requested", 10,
                "returned", 2,
                "page", 1,
                "total", 2,
                "label", "Top searched product news",
                "message", "Two signals",
                "isDefaultNews", true,
                "category", "Default"
        ));

        String body = mvc.perform(get("/api/news/default")
                        .param("limit", "10")
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
    void defaultNewsReturnsJsonWhenRequested() throws Exception {
        when(news.defaults(eq(10), isNull(), any(NewsViewer.class), eq(2))).thenReturn(Map.of(
                "results", List.of(Map.of("title", "Page two", "opportunityScore", 60)),
                "freshness", "last 30 days",
                "page", 2,
                "total", 12,
                "totalPages", 2,
                "label", "Top searched product news",
                "isDefaultNews", true
        ));
        mvc.perform(get("/api/news/default")
                        .param("page", "2")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.results[0].title").value("Page two"));
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
        when(news.industry(eq("Medical"), isNull(), eq(10), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(1)))
                .thenReturn(Map.of(
                        "results", List.of(),
                        "freshness", "warming_up",
                        "requested", 10,
                        "returned", 0,
                        "page", 1,
                        "total", 0,
                        "label", "Medical products"
                ));
        mvc.perform(get("/api/news/Medical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freshness").value("warming_up"))
                .andExpect(jsonPath("$.data.returned").value(0));
    }

    @Test
    void industryNewsStreamsNdjsonWhenRequested() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), eq(10), eq("products"), eq(1), isNull(), any(NewsViewer.class), eq(1)))
                .thenReturn(Map.of(
                        "results", List.of(Map.of("title", "Today launch", "url", "https://a.example/1", "opportunityScore", 80)),
                        "freshness", "fresh",
                        "windowDays", 1,
                        "requested", 10,
                        "returned", 1,
                        "page", 1,
                        "total", 1,
                        "label", "Medical products"
                ));
        String body = mvc.perform(get("/api/news/Medical")
                        .param("window", "1d")
                        .accept(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("\"type\":\"open\"").contains("Today launch").contains("\"type\":\"done\"");
    }

    @Test
    void thirtyDayWindowIsDefault() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), eq("Paediatrics"), eq(10), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(1)))
                .thenReturn(Map.of(
                        "results", List.of(Map.of("title", "A launch", "opportunityScore", 70)),
                        "freshness", "last 30 days",
                        "requested", 10,
                        "returned", 1,
                        "page", 1,
                        "total", 1,
                        "sort", "publishedAtDesc",
                        "label", "Paediatrics products"
                ));
        mvc.perform(get("/api/news/Medical").param("sub", "Paediatrics").param("intent", "products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freshness").value("last 30 days"))
                .andExpect(jsonPath("$.data.sort").value("publishedAtDesc"))
                .andExpect(jsonPath("$.data.results[0].opportunityScore").value(70));
        verify(news).industry(eq("Medical"), eq("Paediatrics"), eq(10), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(1));
    }

    @Test
    void pageParamIsPassedThrough() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), eq(10), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(3)))
                .thenReturn(Map.of(
                        "results", List.of(),
                        "page", 3,
                        "totalPages", 4,
                        "hasMore", true,
                        "label", "Medical products"
                ));
        mvc.perform(get("/api/news/Medical").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void pageBelowOneIsRejected() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        mvc.perform(get("/api/news/Medical").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
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
        when(news.industry(eq("Medical"), isNull(), eq(10), eq("products"), eq(30), eq("US"), any(NewsViewer.class), eq(1)))
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
    void ideasFromNewsStreamsNdjson() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID newsId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(userId, "a@gyanwire.com", "user"), null)
        );
        doAnswer(invocation -> {
            IdeaSink sink = invocation.getArgument(3);
            sink.status("brief");
            sink.meta(Map.of(
                    "headline", "A launch",
                    "url", "https://news.example/a",
                    "industry", "AI",
                    "brief", Map.of("headline", "A launch")
            ));
            sink.status("angles");
            sink.item(Map.of("id", "ni-1", "title", "First idea", "score", 80));
            sink.item(Map.of("id", "ni-2", "title", "Second idea", "score", 70));
            return null;
        }).when(newsIdeas).ideasFromNewsId(eq(userId), any(), eq(newsId), any(IdeaSink.class));

        String body = mvc.perform(post("/api/news/" + newsId + "/ideas")
                        .accept(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String[] lines = body.split("\n");
        assertThat(lines).hasSize(7);
        assertThat(lines[0]).contains("\"type\":\"open\"");
        assertThat(lines[1]).contains("\"type\":\"status\"").contains("brief");
        assertThat(lines[2]).contains("\"type\":\"meta\"").contains("A launch").doesNotContain("\"ideas\"");
        assertThat(lines[3]).contains("\"type\":\"status\"").contains("angles");
        assertThat(lines[4]).contains("\"type\":\"item\"").contains("First idea");
        assertThat(lines[5]).contains("\"type\":\"item\"").contains("Second idea");
        assertThat(lines[6]).contains("\"type\":\"done\"").contains("\"returned\":2");
    }

    @Test
    void videoResearchReturnsPackForNewsId() throws Exception {
        UUID newsId = UUID.randomUUID();
        NewsRow row = new NewsRow()
                .id(newsId)
                .url("https://news.example/anthropic-law")
                .title("Anthropic proposes AI law");
        when(store.findById(newsId)).thenReturn(row);
        when(videoResearch.find("https://news.example/anthropic-law")).thenReturn(new VideoResearchPack(
                "abc",
                VideoResearchPack.STATUS_READY,
                Map.of(
                        "whatHappened", "Anthropic asked lawmakers for AI rules.",
                        "whyItMatters", "Rules would shape how models ship.",
                        "facts", List.of(Map.of("id", "vr1", "text", "Anthropic proposed a law."))
                ),
                null,
                java.time.Instant.now()
        ));

        mvc.perform(get("/api/news/" + newsId + "/video-research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.summary.whatHappened").value("Anthropic asked lawmakers for AI rules."));

        // Already ready — do not restart or wait.
        verify(videoResearch, never()).startIfNeeded(any(), any(), any(), any());
        verify(videoResearch, never()).awaitReady(any(), any());
    }

    @Test
    void videoResearchRequiresValidNewsId() throws Exception {
        mvc.perform(get("/api/news/not-a-uuid/video-research"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void dismissRequiresSignIn() throws Exception {
        mvc.perform(post("/api/news/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void dismissRequiresItemIdOrUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(userId, "u@example.com", "user"), null)
        );
        mvc.perform(post("/api/news/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void dismissSavesAndInvalidatesViewerCache() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(userId, "u@example.com", "user"), null)
        );
        mvc.perform(post("/api/news/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"url\":\"https://a.example/1\",\"industry\":\"AI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dismissed").value(true))
                .andExpect(jsonPath("$.data.itemId").value(itemId.toString()));
        verify(store).saveDismissal(eq(itemId), eq("https://a.example/1"), eq(userId), any(), eq("AI"));
        verify(news).invalidateViewer(any(NewsViewer.class));
    }

    @Test
    void industryUsesThirtyDayWindowByDefault() throws Exception {
        when(industries.listCatalog()).thenReturn(List.of(catalog()));
        when(news.industry(eq("Medical"), isNull(), anyInt(), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(1)))
                .thenReturn(Map.of("results", List.of(), "label", "Medical products", "freshness", "last 30 days"));
        mvc.perform(get("/api/news/Medical"))
                .andExpect(status().isOk());
        verify(news).industry(eq("Medical"), isNull(), anyInt(), eq("products"), eq(30), isNull(), any(NewsViewer.class), eq(1));
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
