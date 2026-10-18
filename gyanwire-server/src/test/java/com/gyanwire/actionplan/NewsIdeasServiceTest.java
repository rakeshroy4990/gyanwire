package com.gyanwire.actionplan;

import com.gyanwire.actionplan.angle.AngleService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.ArticleText;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.actionplan.news.NewsBriefService;
import com.gyanwire.actionplan.news.NewsResolveService;
import com.gyanwire.actionplan.research.VideoResearchService;
import com.gyanwire.ideas.IdeaSink;
import com.gyanwire.news.NewsStore;
import com.gyanwire.usage.UsageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsIdeasServiceTest {

    @Mock NewsStore newsStore;
    @Mock NewsBriefService briefs;
    @Mock NewsResolveService resolve;
    @Mock AngleService angles;
    @Mock FormatRegistry formats;
    @Mock UsageService usage;
    @Mock JdbcTemplate jdbc;
    @Mock VideoResearchService videoResearch;
    @Mock ActionPlanStore store;

    NewsIdeasService service;

    @BeforeEach
    void setUp() {
        service = new NewsIdeasService(
                newsStore, briefs, resolve, angles, formats, usage, jdbc, videoResearch, store);
        lenient().when(usage.resolveUserPlan(any())).thenReturn(Map.of("dailyIdeaLimit", 99));
        lenient().when(usage.countToday(any(), any(), eq("idea"))).thenReturn(0L);
    }

    @Test
    void aggregatorUrlNeverCallsResolve() {
        String summary = "Anthropic announced Claude Haiku for builders. ".repeat(4);
        String title = "Anthropic launches Claude Haiku";
        ArticleText article = service.resolveIdeasText(
                "https://news.google.com/rss/articles/CBMiEXAMPLE",
                title,
                "Google News",
                summary);

        assertThat(article.empty()).isFalse();
        assertThat(article.text()).contains("Anthropic");
        verify(resolve, never()).resolve(anyString(), anyString(), anyString(), anyInt());
        verify(resolve, never()).resolve(anyString(), anyString(), anyString());
    }

    @Test
    void longSummarySkipsPublisherFetch() {
        String summary = "x".repeat(200);
        String title = "y".repeat(100);
        ArticleText article = service.resolveIdeasText(
                "https://techcrunch.com/example",
                title,
                "TechCrunch",
                summary);

        assertThat(article.empty()).isFalse();
        verify(resolve, never()).resolve(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void previewItemArrivesBeforeAnglesPickReturns() throws Exception {
        UUID userId = UUID.randomUUID();
        NewsBrief brief = usableBrief();
        when(store.findIdeaCache(anyString())).thenReturn(null);
        when(briefs.localBrief(any(), any(), any(), any(), any())).thenReturn(brief);
        when(videoResearch.startIfNeeded(nullable(UUID.class), any(), any(), any())).thenReturn(false);
        when(formats.select(eq(240), eq(false))).thenReturn(new FormatSpec(
                "short_vertical",
                "Short vertical",
                List.of("youtube_shorts"),
                "9:16",
                "1080x1920",
                60,
                new int[]{45, 60},
                new int[]{90, 130},
                240,
                "short_vertical_steps",
                "social_default",
                false,
                List.of()));

        CountDownLatch anglesEntered = new CountDownLatch(1);
        CountDownLatch allowAngles = new CountDownLatch(1);
        when(angles.pick(any(), any(), eq(userId))).thenAnswer(invocation -> {
            anglesEntered.countDown();
            assertThat(allowAngles.await(2, TimeUnit.SECONDS)).isTrue();
            return List.of(angleRow("Explain Haiku for builders"));
        });

        List<String> events = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        IdeaSink sink = recordingSink(events);

        Thread worker = new Thread(() -> {
            try {
                service.ideasFromUrl(
                        userId,
                        "ip",
                        null,
                        "https://example.com/haiku",
                        "Anthropic launches Claude Haiku",
                        "Example",
                        longSummary(),
                        "AI",
                        sink);
            } catch (Throwable ex) {
                error.set(ex);
            }
        });
        worker.start();

        assertThat(anglesEntered.await(3, TimeUnit.SECONDS))
                .as("angles.pick should block after preview; worker error=%s", error.get())
                .isTrue();
        assertThat(events).contains("preview");
        assertThat(events).doesNotContain("item:Explain Haiku for builders");

        allowAngles.countDown();
        worker.join(5_000);
        assertThat(error.get()).isNull();
        assertThat(events).contains("preview", "item:Explain Haiku for builders");
        verify(store).saveIdeaCache(anyString(), any());
    }

    @Test
    void cacheHitEmitsItemsWithoutAnglesOrFetch() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("headline", "Cached launch");
        cached.put("url", "https://example.com/cached");
        cached.put("industry", "AI");
        cached.put("brief", Map.of("headline", "Cached launch"));
        cached.put("ideas", List.of(Map.of("id", "ni-1", "title", "Cached idea", "score", 80)));
        when(store.findIdeaCache(anyString())).thenReturn(cached);
        when(videoResearch.startIfNeeded(nullable(UUID.class), any(), nullable(NewsBrief.class), any()))
                .thenReturn(true);

        List<String> events = new ArrayList<>();
        service.ideasFromUrl(
                userId,
                "ip",
                null,
                "https://example.com/cached",
                "Cached launch",
                "Example",
                longSummary(),
                "AI",
                recordingSink(events));

        assertThat(events).contains("item:Cached idea", "research:started");
        verify(videoResearch).startIfNeeded(nullable(UUID.class), eq("https://example.com/cached"), nullable(NewsBrief.class), eq(userId));
        verify(angles, never()).pick(any(), any(), any());
        verify(briefs, never()).localBrief(any(), any(), any(), any(), any());
        verify(resolve, never()).resolve(anyString(), anyString(), anyString(), anyInt());
        verify(usage).recordUsage(eq(userId), eq("ip"), eq("idea"), any());
    }

    private static IdeaSink recordingSink(List<String> events) {
        return new IdeaSink() {
            @Override
            public void status(String stage) {
                events.add("status:" + stage);
            }

            @Override
            public void meta(Map<String, Object> frame) {
                events.add("meta");
            }

            @Override
            public void item(Map<String, Object> row) {
                if (Boolean.TRUE.equals(row.get("preview"))) {
                    events.add("preview");
                } else {
                    events.add("item:" + row.get("title"));
                }
            }

            @Override
            public void research(Map<String, Object> frame) {
                events.add("research:" + frame.getOrDefault("status", ""));
            }
        };
    }

    private static NewsBrief usableBrief() {
        return new NewsBrief(
                UUID.randomUUID().toString(),
                "Anthropic launches Claude Haiku",
                "https://example.com/haiku",
                "example.com",
                null,
                List.of("Anthropic", "Claude"),
                List.of(
                        new NewsBrief.Fact("f1", "Anthropic launched Claude Haiku.", "Anthropic launched Claude Haiku.", "high"),
                        new NewsBrief.Fact("f2", "Builders can use the model today.", "Builders can use the model today.", "medium"),
                        new NewsBrief.Fact("f3", "Pricing starts lower than Sonnet.", "Pricing starts lower than Sonnet.", "medium")
                ),
                List.of("AI", "builders"),
                12);
    }

    private static Map<String, Object> angleRow(String oneLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("oneLine", oneLine);
        row.put("forWhom", "builders");
        row.put("whyNow", "The model just shipped");
        row.put("test", "100 views in 48 h");
        row.put("effortMinutes", 240);
        row.put("bestBet", true);
        row.put("score", 80);
        return row;
    }

    private static String longSummary() {
        return ("Anthropic announced Claude Haiku for builders with lower pricing and faster replies for product work. "
                + "Teams can ship chat features without waiting on larger models this week. ").repeat(3);
    }
}
