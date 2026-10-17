package com.gyanwire.actionplan;

import com.gyanwire.actionplan.angle.AngleService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.actionplan.news.NewsBriefService;
import com.gyanwire.actionplan.research.VideoResearchService;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.ideas.CollectingIdeaSink;
import com.gyanwire.ideas.IdeaSink;
import com.gyanwire.news.NewsRow;
import com.gyanwire.news.NewsStore;
import com.gyanwire.research.ResearchException;
import com.gyanwire.usage.UsageService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NewsBrief-first ideas for the IdeasPanel (N2). Metered as {@code idea}.
 */
@Service
public class NewsIdeasService {

    private final NewsStore newsStore;
    private final NewsBriefService briefs;
    private final AngleService angles;
    private final FormatRegistry formats;
    private final UsageService usage;
    private final JdbcTemplate jdbc;
    private final VideoResearchService videoResearch;

    public NewsIdeasService(
            NewsStore newsStore,
            NewsBriefService briefs,
            AngleService angles,
            FormatRegistry formats,
            UsageService usage,
            JdbcTemplate jdbc,
            VideoResearchService videoResearch
    ) {
        this.newsStore = newsStore;
        this.briefs = briefs;
        this.angles = angles;
        this.formats = formats;
        this.usage = usage;
        this.jdbc = jdbc;
        this.videoResearch = videoResearch;
    }

    public Map<String, Object> ideasFromNewsId(UUID userId, String ipHash, UUID newsId) {
        CollectingIdeaSink sink = new CollectingIdeaSink();
        ideasFromNewsId(userId, ipHash, newsId, sink);
        return sink.result();
    }

    public void ideasFromNewsId(UUID userId, String ipHash, UUID newsId, IdeaSink sink) {
        NewsRow row = newsStore.findById(newsId);
        if (row == null) {
            throw new ResearchException("News item not found.", "NOT_FOUND", 404);
        }
        ideasFromUrl(
                userId,
                ipHash,
                newsId,
                row.url(),
                row.title(),
                row.sourceName(),
                row.summary(),
                row.industry(),
                sink == null ? IdeaSink.noop() : sink);
    }

    public Map<String, Object> ideasFromUrl(
            UUID userId,
            String ipHash,
            String url,
            String title,
            String source,
            String summary,
            String industry
    ) {
        CollectingIdeaSink sink = new CollectingIdeaSink();
        ideasFromUrl(userId, ipHash, null, url, title, source, summary, industry, sink);
        return sink.result();
    }

    public void ideasFromUrl(
            UUID userId,
            String ipHash,
            String url,
            String title,
            String source,
            String summary,
            String industry,
            IdeaSink sink
    ) {
        ideasFromUrl(userId, ipHash, null, url, title, source, summary, industry, sink);
    }

    public void ideasFromUrl(
            UUID userId,
            String ipHash,
            UUID newsId,
            String url,
            String title,
            String source,
            String summary,
            String industry,
            IdeaSink sink
    ) {
        IdeaSink out = sink == null ? IdeaSink.noop() : sink;
        assertIdeaQuota(userId, ipHash);
        out.status("brief");
        NewsBrief brief = briefs.extract(url, title, source, null, userId);
        if (!brief.usable()) {
            // Try with summary as paste so thin RSS still yields ideas.
            if (summary != null && summary.length() > 80) {
                brief = briefs.extract(url, title, source, summary, userId);
            }
        }
        if (!brief.usable()) {
            throw new ActionPlanException(
                    "Could not read enough of this article for ideas",
                    "ARTICLE_UNREADABLE",
                    422,
                    Map.of("reason", "insufficient_facts", "pasteRequired", true));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("newsId", newsId == null ? brief.newsId() : newsId.toString());
        meta.put("headline", brief.headline());
        meta.put("url", brief.publisherUrl());
        meta.put("industry", industry == null ? "" : industry);
        meta.put("brief", brief.toMap());
        out.meta(meta);

        // Kick multi-source video research without waiting; ideas stream continues.
        boolean researchStarted = videoResearch.startIfNeeded(newsId, url, brief, userId);

        out.status("angles");
        FormatSpec format = formats.select(240, false);
        List<Map<String, Object>> anglesList = angles.pick(brief, format, userId);
        List<Map<String, Object>> ideas = new ArrayList<>();
        boolean hasQuick = false;
        int i = 0;
        for (Map<String, Object> angle : anglesList) {
            i++;
            Map<String, Object> idea = toIdeaCard(angle, brief, i);
            int effort = intVal(idea.get("effortMin"), 240);
            if (effort <= 240 && !hasQuick) {
                idea.put("quickWin", effort <= 240);
                hasQuick = effort <= 240;
            }
            if (Boolean.TRUE.equals(angle.get("bestBet"))) {
                idea.put("bestBet", true);
            }
            // Drop ideas without evidence
            if (idea.get("facts") instanceof List<?> facts && !facts.isEmpty()) {
                ideas.add(idea);
            }
        }
        if (ideas.isEmpty()) {
            ideas.add(fallbackIdea(brief));
        }
        // Ensure one quick win and one best bet
        if (ideas.stream().noneMatch(r -> Boolean.TRUE.equals(r.get("bestBet")))) {
            ideas.get(0).put("bestBet", true);
        }
        if (ideas.stream().noneMatch(r -> Boolean.TRUE.equals(r.get("quickWin")))) {
            Map<String, Object> first = ideas.get(0);
            first.put("quickWin", true);
            first.put("effortMin", Math.min(intVal(first.get("effortMin"), 240), 240));
            first.put("effortLabel", "4 h");
        }

        for (Map<String, Object> idea : ideas) {
            out.item(idea);
        }
        if (researchStarted) {
            out.research(Map.of("status", "started"));
        }
        usage.recordUsage(userId, ipHash, "idea", java.math.BigDecimal.ZERO);
    }

    public void rate(UUID userId, String ideaKey, String rating) {
        if (userId == null || ideaKey == null || ideaKey.isBlank()) {
            throw new ResearchException("Sign in and send an idea id.", "VALIDATION_ERROR", 400);
        }
        String clean = rating == null ? "" : rating.trim().toLowerCase();
        if (!"useful".equals(clean) && !"not_useful".equals(clean)) {
            throw new ResearchException("Rating must be useful or not_useful.", "VALIDATION_ERROR", 400);
        }
        jdbc.update("""
                INSERT INTO idea_ratings (id, user_id, idea_key, rating)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, idea_key) DO UPDATE SET rating = EXCLUDED.rating, created_at = now()
                """, UUID.randomUUID(), userId, ideaKey.trim(), clean);
    }

    private Map<String, Object> toIdeaCard(Map<String, Object> angle, NewsBrief brief, int index) {
        Map<String, Object> idea = new LinkedHashMap<>();
        String id = "ni-" + index + "-" + (brief.newsId() == null ? "x" : brief.newsId().substring(0, Math.min(8, brief.newsId().length())));
        idea.put("id", id);
        idea.put("title", str(angle.get("oneLine")));
        idea.put("type", "social_media");
        idea.put("audience", str(angle.get("forWhom")));
        idea.put("whyNow", str(angle.get("whyNow")));
        idea.put("firstTest", str(angle.get("test")));
        int effort = intVal(angle.get("effortMinutes"), 240);
        idea.put("effortMin", effort);
        idea.put("effortLabel", effort <= 240 ? (effort / 60) + " h" : (effort / 60) + " h");
        idea.put("bestBet", Boolean.TRUE.equals(angle.get("bestBet")));
        idea.put("quickWin", effort <= 240);
        idea.put("demand", meter(70));
        idea.put("effortMeter", meter(effort <= 240 ? 30 : 60));
        idea.put("competition", meter(45));
        List<Map<String, Object>> facts = new ArrayList<>();
        if (brief.facts() != null) {
            for (NewsBrief.Fact fact : brief.facts()) {
                Map<String, Object> chip = new LinkedHashMap<>();
                chip.put("id", fact.id());
                chip.put("text", fact.text());
                chip.put("sourceSpan", fact.sourceSpan());
                facts.add(chip);
            }
        }
        idea.put("facts", facts);
        idea.put("score", intVal(angle.get("score"), 50));
        return idea;
    }

    private Map<String, Object> fallbackIdea(NewsBrief brief) {
        Map<String, Object> angle = new LinkedHashMap<>();
        String entity = brief.entities() != null && !brief.entities().isEmpty() ? brief.entities().get(0) : "this news";
        angle.put("oneLine", "Post a short vertical explaining " + entity + " for builders");
        angle.put("forWhom", brief.whoItAffects() != null && !brief.whoItAffects().isEmpty()
                ? brief.whoItAffects().get(0) : "builders and creators");
        angle.put("whyNow", "Tied to fact f1 from the article");
        angle.put("test", "100 views in 48 h");
        angle.put("effortMinutes", 240);
        angle.put("bestBet", true);
        angle.put("score", 60);
        return toIdeaCard(angle, brief, 1);
    }

    private static Map<String, Object> meter(int value) {
        return Map.of("value", Math.max(0, Math.min(100, value)), "label", "estimate");
    }

    private void assertIdeaQuota(UUID userId, String ipHash) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        int limit = ((Number) plan.getOrDefault("dailyIdeaLimit", 3)).intValue();
        long used = usage.countToday(userId, ipHash, "idea");
        if (limit > 0 && limit < UsageService.UNLIMITED && used >= limit) {
            throw new PlanLimitException(
                    "Daily idea limit reached.",
                    "LIMIT_REACHED",
                    Map.of("upgradeUrl", "/pricing", "used", used, "limit", limit));
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static int intVal(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
