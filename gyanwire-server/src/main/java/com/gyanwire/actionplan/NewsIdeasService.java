package com.gyanwire.actionplan;

import com.gyanwire.actionplan.angle.AngleService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.ArticleText;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.actionplan.news.NewsBriefService;
import com.gyanwire.actionplan.news.NewsResolveService;
import com.gyanwire.actionplan.research.VideoResearchPack;
import com.gyanwire.actionplan.research.VideoResearchService;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.ideas.CollectingIdeaSink;
import com.gyanwire.ideas.IdeaSink;
import com.gyanwire.news.NewsRow;
import com.gyanwire.news.NewsStore;
import com.gyanwire.news.NewsTexts;
import com.gyanwire.research.ResearchException;
import com.gyanwire.usage.UsageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NewsBrief-first ideas for the IdeasPanel (N2). Metered as {@code idea}.
 * Critical path: title/summary (or a short fetch) → local brief → preview card → one angles call.
 */
@Service
public class NewsIdeasService {

    private static final Logger log = LoggerFactory.getLogger(NewsIdeasService.class);

    /** Skip live fetch when title+summary is at least this long. */
    static final int SUMMARY_SKIP_FETCH_CHARS = 280;
    /** Cap publisher fetch on the ideas path. */
    static final int IDEAS_FETCH_TIMEOUT_MS = 3_000;
    /** Fall back to RSS summary when longer than this. */
    static final int SUMMARY_FALLBACK_CHARS = 80;

    private final NewsStore newsStore;
    private final NewsBriefService briefs;
    private final NewsResolveService resolve;
    private final AngleService angles;
    private final FormatRegistry formats;
    private final UsageService usage;
    private final JdbcTemplate jdbc;
    private final VideoResearchService videoResearch;
    private final ActionPlanStore store;

    public NewsIdeasService(
            NewsStore newsStore,
            NewsBriefService briefs,
            NewsResolveService resolve,
            AngleService angles,
            FormatRegistry formats,
            UsageService usage,
            JdbcTemplate jdbc,
            VideoResearchService videoResearch,
            ActionPlanStore store
    ) {
        this.newsStore = newsStore;
        this.briefs = briefs;
        this.resolve = resolve;
        this.angles = angles;
        this.formats = formats;
        this.usage = usage;
        this.jdbc = jdbc;
        this.videoResearch = videoResearch;
        this.store = store;
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
        long started = System.nanoTime();
        consumeIdeaQuota(userId, ipHash);

        String urlHash = NewsBriefService.urlHash(url);
        Map<String, Object> cached = store.findIdeaCache(urlHash);
        if (cached != null) {
            emitCached(out, cached, newsId, industry);
            // Keep video-research warm on cache hits; emit ready+summary when already available.
            emitResearchStatus(out, newsId, url, briefFromCache(cached, title, url), userId);
            int ideaCount = ideaCount(cached.get("ideas"));
            log.info(
                    "news.ideas path=cache totalMs={} ideas={} newsId={}",
                    msSince(started), ideaCount, newsId);
            return;
        }

        out.status("brief");
        long resolveStart = System.nanoTime();
        ArticleText article = resolveIdeasText(url, title, source, summary);
        long resolveMs = msSince(resolveStart);
        log.debug("news.ideas stage=resolve ms={} empty={}", resolveMs, article.empty());

        String text = article.empty() ? titleAndSummary(title, summary) : article.text();
        String publisherUrl = article.empty() || article.publisherUrl().isBlank()
                ? (url == null ? "" : url)
                : article.publisherUrl();
        String publisher = article.empty() || article.publisher().isBlank()
                ? (source == null ? "" : source)
                : article.publisher();

        long briefStart = System.nanoTime();
        NewsBrief brief = briefs.localBrief(title, publisherUrl, publisher, text, industry);
        long briefMs = msSince(briefStart);
        log.debug("news.ideas stage=brief ms={} usable={}", briefMs, brief.usable());
        if (!brief.usable()) {
            log.info(
                    "news.ideas path=unreadable totalMs={} resolveMs={} briefMs={} newsId={}",
                    msSince(started), resolveMs, briefMs, newsId);
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

        // Start research early so it can finish while angles run.
        videoResearch.startIfNeeded(newsId, url, brief, userId);

        Map<String, Object> preview = fallbackIdea(brief);
        preview.put("preview", true);
        out.item(preview);
        long firstItemMs = msSince(started);
        log.debug("news.ideas stage=preview ms={}", firstItemMs);

        out.status("angles");
        long anglesStart = System.nanoTime();
        FormatSpec format = formats.select(240, false);
        List<Map<String, Object>> anglesList = angles.pick(brief, format, userId);
        List<Map<String, Object>> ideas = buildIdeaCards(anglesList, brief);
        long anglesMs = msSince(anglesStart);
        log.debug("news.ideas stage=angles ms={} cards={}", anglesMs, ideas.size());
        if (ideas.isEmpty()) {
            ideas = List.of(preview);
        } else {
            for (Map<String, Object> idea : ideas) {
                out.item(idea);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>(meta);
        payload.put("ideas", ideas);
        store.saveIdeaCache(urlHash, payload);

        emitResearchStatus(out, newsId, url, brief, userId);
        log.info(
                "news.ideas path=fresh totalMs={} firstItemMs={} resolveMs={} briefMs={} anglesMs={} ideas={} newsId={}",
                msSince(started), firstItemMs, resolveMs, briefMs, anglesMs, ideas.size(), newsId);
    }

    private static long msSince(long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
        return ms < 0 ? 0 : ms;
    }

    private static int ideaCount(Object raw) {
        if (raw instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    /**
     * Prefer stored title/summary; only fetch publishers with a short timeout when the snippet is thin.
     * Aggregator URLs never fetch.
     */
    ArticleText resolveIdeasText(String url, String title, String source, String summary) {
        String combined = titleAndSummary(title, summary);
        if (NewsTexts.aggregator(url) || hostIsGoogleNews(url)) {
            return ArticleText.snippet(url, source, combined, NewsBriefService.IDEAS_SNIPPET_MIN);
        }
        if (combined.length() >= SUMMARY_SKIP_FETCH_CHARS) {
            return ArticleText.snippet(url, source, combined, NewsBriefService.IDEAS_SNIPPET_MIN);
        }
        ArticleText fetched = resolve.resolve(url, title, source, IDEAS_FETCH_TIMEOUT_MS);
        if (fetched != null && !fetched.empty()) {
            return fetched;
        }
        if (summary != null && summary.length() > SUMMARY_FALLBACK_CHARS) {
            return ArticleText.snippet(url, source, combined, NewsBriefService.IDEAS_SNIPPET_MIN);
        }
        return fetched == null ? ArticleText.empty("fetch_error") : fetched;
    }

    /**
     * Tell the client about video-research state. When the pack is already ready,
     * include the summary so the ideas page can show "Ready for the video" without polling.
     */
    private void emitResearchStatus(IdeaSink out, UUID newsId, String url, NewsBrief brief, UUID userId) {
        videoResearch.startIfNeeded(newsId, url, brief, userId);
        VideoResearchPack pack = videoResearch.find(url);
        if (pack == null && newsId != null) {
            pack = videoResearch.findByNewsId(newsId);
        }
        if (pack != null && pack.ready() && pack.summary() != null && !pack.summary().isEmpty()) {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("status", VideoResearchPack.STATUS_READY);
            frame.put("summary", pack.summary());
            out.research(frame);
            return;
        }
        if (pack != null && pack.failed()) {
            out.research(Map.of("status", VideoResearchPack.STATUS_FAILED));
            return;
        }
        out.research(Map.of("status", "started"));
    }

    @SuppressWarnings("unchecked")
    private static NewsBrief briefFromCache(Map<String, Object> cached, String title, String url) {
        Object raw = cached == null ? null : cached.get("brief");
        if (raw instanceof Map<?, ?> map) {
            return NewsBrief.fromMap((Map<String, Object>) map);
        }
        String headline = cached != null && cached.get("headline") != null
                ? String.valueOf(cached.get("headline"))
                : (title == null ? "" : title);
        String publisherUrl = cached != null && cached.get("url") != null
                ? String.valueOf(cached.get("url"))
                : (url == null ? "" : url);
        return NewsBrief.insufficient(headline, publisherUrl);
    }

    private void emitCached(IdeaSink out, Map<String, Object> cached, UUID newsId, String industry) {
        out.status("brief");
        Map<String, Object> meta = new LinkedHashMap<>();
        if (cached.get("headline") != null) {
            meta.put("headline", cached.get("headline"));
        }
        if (cached.get("url") != null) {
            meta.put("url", cached.get("url"));
        }
        if (cached.get("brief") != null) {
            meta.put("brief", cached.get("brief"));
        }
        meta.put("newsId", newsId == null
                ? cached.getOrDefault("newsId", "")
                : newsId.toString());
        meta.put("industry", industry == null
                ? String.valueOf(cached.getOrDefault("industry", ""))
                : industry);
        out.meta(meta);
        out.status("angles");
        Object raw = cached.get("ideas");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Map<String, Object> idea = new LinkedHashMap<>();
                    row.forEach((k, v) -> idea.put(String.valueOf(k), v));
                    out.item(idea);
                }
            }
        }
    }

    private List<Map<String, Object>> buildIdeaCards(List<Map<String, Object>> anglesList, NewsBrief brief) {
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
            if (idea.get("facts") instanceof List<?> facts && !facts.isEmpty()) {
                ideas.add(idea);
            }
        }
        if (ideas.isEmpty()) {
            return List.of();
        }
        if (ideas.stream().noneMatch(r -> Boolean.TRUE.equals(r.get("bestBet")))) {
            ideas.get(0).put("bestBet", true);
        }
        if (ideas.stream().noneMatch(r -> Boolean.TRUE.equals(r.get("quickWin")))) {
            Map<String, Object> first = ideas.get(0);
            first.put("quickWin", true);
            first.put("effortMin", Math.min(intVal(first.get("effortMin"), 240), 240));
            first.put("effortLabel", "4 h");
        }
        return ideas;
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

    private static String titleAndSummary(String title, String summary) {
        String head = title == null ? "" : title.trim();
        String body = summary == null ? "" : summary.trim();
        if (head.isBlank()) {
            return body;
        }
        if (body.isBlank()) {
            return head;
        }
        return (head + ". " + body).replaceAll("\\s+", " ").trim();
    }

    private static boolean hostIsGoogleNews(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("news.google.com")
                    || host.endsWith(".news.google.com"));
        } catch (Exception ex) {
            return false;
        }
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

    private static Map<String, Object> meter(int value) {
        return Map.of("value", Math.max(0, Math.min(100, value)), "label", "estimate");
    }

    private void consumeIdeaQuota(UUID userId, String ipHash) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        int limit = ((Number) plan.getOrDefault("dailyIdeaLimit", 3)).intValue();
        usage.reserveOrThrow(userId, ipHash, "idea", limit, "Daily idea limit reached.");
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
