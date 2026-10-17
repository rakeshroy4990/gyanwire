package com.gyanwire.actionplan.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.actionplan.news.NewsBriefService;
import com.gyanwire.ideas.pipeline.IdeaStageClient;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.DiscoverService;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.research.engine.ScrapeService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background multi-source research for video scripts, keyed by news URL hash.
 */
@Service
public class VideoResearchService {

    public static final Duration CACHE_TTL = Duration.ofHours(24);
    public static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_PAGES = 6;
    private static final int DISCOVER_LIMIT = 10;
    private static final int SCRAPE_CONCURRENCY = 4;
    private static final int SCRAPE_TIMEOUT_MS = 8000;
    private static final int PAGE_TEXT_CAP = 4000;

    private final VideoResearchStore store;
    private final DiscoverService discover;
    private final ScrapeService scrape;
    private final ObjectProvider<IdeaStageClient> stageClient;
    private final ObjectMapper mapper;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "video-research");
        thread.setDaemon(true);
        return thread;
    });

    public VideoResearchService(
            VideoResearchStore store,
            DiscoverService discover,
            ScrapeService scrape,
            ObjectProvider<IdeaStageClient> stageClient,
            ObjectMapper mapper
    ) {
        this.store = store;
        this.discover = discover;
        this.scrape = scrape;
        this.stageClient = stageClient;
        this.mapper = mapper;
    }

    /**
     * Enqueues research if needed. Returns true when a job was started (or already running/ready).
     */
    public boolean startIfNeeded(UUID newsId, String newsUrl, NewsBrief brief, UUID userId) {
        String urlHash = NewsBriefService.urlHash(newsUrl);
        if (urlHash == null || urlHash.isBlank()) {
            return false;
        }
        VideoResearchPack existing = store.find(urlHash);
        if (existing != null && VideoResearchStore.fresh(existing, CACHE_TTL)
                && (existing.ready() || existing.running())) {
            return true;
        }
        if (!store.claimRunning(urlHash, newsId, CACHE_TTL)) {
            VideoResearchPack after = store.find(urlHash);
            return after != null && (after.ready() || after.running());
        }
        String headline = brief == null ? "" : brief.headline();
        List<String> entities = brief == null || brief.entities() == null ? List.of() : brief.entities();
        String excludeUrl = newsUrl == null ? "" : newsUrl;
        workers.submit(() -> runJob(urlHash, newsId, excludeUrl, headline, entities, userId));
        return true;
    }

    public VideoResearchPack find(String newsUrl) {
        return store.find(NewsBriefService.urlHash(newsUrl));
    }

    public VideoResearchPack findByNewsId(UUID newsId) {
        return store.findByNewsId(newsId);
    }

    /**
     * Waits until the pack is ready/failed or the timeout elapses.
     */
    public VideoResearchPack awaitReady(String newsUrl, Duration timeout) {
        String urlHash = NewsBriefService.urlHash(newsUrl);
        Duration wait = timeout == null ? AWAIT_TIMEOUT : timeout;
        long deadline = System.currentTimeMillis() + Math.max(0, wait.toMillis());
        VideoResearchPack pack = store.find(urlHash);
        while (pack != null && pack.running() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            pack = store.find(urlHash);
        }
        return pack;
    }

    void runJob(
            String urlHash,
            UUID newsId,
            String excludeUrl,
            String headline,
            List<String> entities,
            UUID userId
    ) {
        try {
            Map<String, Object> summary = buildPack(excludeUrl, headline, entities, userId);
            if (summary == null || !hasGroundedFacts(summary)) {
                store.markFailed(urlHash, "Could not ground enough facts from other sites");
                return;
            }
            store.markReady(urlHash, summary);
        } catch (Exception ex) {
            store.markFailed(urlHash, ex.getMessage() == null ? "Research failed" : ex.getMessage());
        }
    }

    Map<String, Object> buildPack(String excludeUrl, String headline, List<String> entities, UUID userId)
            throws Exception {
        String query = buildQuery(headline, entities);
        List<Map<String, String>> discovered;
        try {
            discovered = discover.discoverPages(query, DISCOVER_LIMIT, List.of());
        } catch (Exception ex) {
            return null;
        }
        List<String> urls = new ArrayList<>();
        String excludeNorm = normalizeUrl(excludeUrl);
        for (Map<String, String> row : discovered) {
            String url = row == null ? null : row.get("url");
            if (url == null || url.isBlank()) {
                continue;
            }
            if (!excludeNorm.isBlank() && normalizeUrl(url).equals(excludeNorm)) {
                continue;
            }
            if (urls.stream().noneMatch(u -> normalizeUrl(u).equals(normalizeUrl(url)))) {
                urls.add(url);
            }
            if (urls.size() >= MAX_PAGES) {
                break;
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Object>> scraped = scrape.scrapeMany(urls, SCRAPE_CONCURRENCY, SCRAPE_TIMEOUT_MS);
        Map<String, Page> pages = new LinkedHashMap<>();
        for (String url : urls) {
            Map<String, Object> page = scraped.get(url);
            if (page == null || !Boolean.TRUE.equals(page.get("ok"))) {
                continue;
            }
            String text = String.valueOf(page.getOrDefault("text", ""));
            if (text.length() < 120) {
                continue;
            }
            if (text.length() > PAGE_TEXT_CAP) {
                text = text.substring(0, PAGE_TEXT_CAP);
            }
            String title = String.valueOf(page.getOrDefault("title", ""));
            pages.put(normalizeUrl(url), new Page(url, title, text));
        }
        if (pages.isEmpty()) {
            return null;
        }

        Map<String, Object> draft = fromLlm(headline, entities, pages, userId);
        if (draft == null) {
            draft = heuristic(pages, headline);
        }
        Map<String, Object> verified = verify(draft, pages);
        if (!hasGroundedFacts(verified)) {
            Map<String, Object> retry = fromLlm(headline, entities, pages, userId);
            if (retry != null) {
                verified = verify(retry, pages);
            }
        }
        return hasGroundedFacts(verified) ? verified : null;
    }

    static String buildQuery(String headline, List<String> entities) {
        StringBuilder out = new StringBuilder();
        if (headline != null && !headline.isBlank()) {
            out.append(headline.trim());
        }
        if (entities != null) {
            int added = 0;
            for (String entity : entities) {
                if (entity == null || entity.isBlank()) {
                    continue;
                }
                if (out.indexOf(entity) < 0) {
                    if (!out.isEmpty()) {
                        out.append(' ');
                    }
                    out.append(entity.trim());
                    added++;
                }
                if (added >= 3) {
                    break;
                }
            }
        }
        String query = out.toString().replaceAll("\\s+", " ").trim();
        return query.isBlank() ? "news update" : query;
    }

    private Map<String, Object> fromLlm(
            String headline,
            List<String> entities,
            Map<String, Page> pages,
            UUID userId
    ) {
        IdeaStageClient client = stageClient.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            String system = LlmService.prompt("video-research.v1.txt");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("headline", headline == null ? "" : headline);
            payload.put("entities", entities == null ? List.of() : entities);
            List<Map<String, String>> pageRows = new ArrayList<>();
            for (Page page : pages.values()) {
                pageRows.add(Map.of(
                        "url", page.url(),
                        "title", page.title() == null ? "" : page.title(),
                        "text", page.text()));
            }
            payload.put("pages", pageRows);
            String user = mapper.writeValueAsString(payload);
            JsonNode node = client.complete(
                    userId, "video-research.v1", "video-research", system, user, ModelTier.LIGHT);
            String shapeError = validateShape(node);
            if (shapeError != null) {
                node = client.complete(
                        userId,
                        "video-research.v1",
                        "video-research",
                        system,
                        user + "\nValidation error: " + shapeError + "\nReturn JSON that fixes it.",
                        ModelTier.LIGHT);
                if (validateShape(node) != null) {
                    return null;
                }
            }
            return parse(node, pages);
        } catch (Exception ex) {
            return null;
        }
    }

    static String validateShape(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        if (!node.path("facts").isArray() || node.path("facts").isEmpty()) {
            return "need facts";
        }
        return null;
    }

    private Map<String, Object> parse(JsonNode node, Map<String, Page> pages) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whatHappened", node.path("whatHappened").asText("").trim());
        out.put("whyItMatters", node.path("whyItMatters").asText("").trim());
        out.put("facts", parseCited(node.path("facts"), "vr"));
        out.put("reactions", parseCited(node.path("reactions"), "rx"));
        out.put("context", node.path("context").asText("").trim());
        List<String> unconfirmed = new ArrayList<>();
        if (node.path("unconfirmed").isArray()) {
            node.path("unconfirmed").forEach(n -> {
                if (!n.asText("").isBlank()) {
                    unconfirmed.add(n.asText().trim());
                }
            });
        }
        out.put("unconfirmed", unconfirmed);
        List<Map<String, String>> sources = new ArrayList<>();
        if (node.path("sources").isArray()) {
            node.path("sources").forEach(n -> {
                String url = n.path("url").asText("").trim();
                String title = n.path("title").asText("").trim();
                if (!url.isBlank()) {
                    sources.add(Map.of("title", title, "url", url));
                }
            });
        }
        if (sources.isEmpty()) {
            for (Page page : pages.values()) {
                sources.add(Map.of(
                        "title", page.title() == null ? "" : page.title(),
                        "url", page.url()));
            }
        }
        out.put("sources", sources);
        return out;
    }

    private static List<Map<String, Object>> parseCited(JsonNode array, String prefix) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return rows;
        }
        int i = 1;
        for (JsonNode n : array) {
            Map<String, Object> row = new LinkedHashMap<>();
            String id = n.path("id").asText(prefix + i).trim();
            row.put("id", id.isBlank() ? prefix + i : id);
            row.put("text", n.path("text").asText("").trim());
            row.put("sourceUrl", n.path("sourceUrl").asText("").trim());
            row.put("sourceTitle", n.path("sourceTitle").asText("").trim());
            row.put("sourceSpan", n.path("sourceSpan").asText("").trim());
            rows.add(row);
            i++;
        }
        return rows;
    }

    static Map<String, Object> verify(Map<String, Object> draft, Map<String, Page> pages) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whatHappened", str(draft.get("whatHappened")));
        out.put("whyItMatters", str(draft.get("whyItMatters")));
        out.put("context", str(draft.get("context")));
        out.put("unconfirmed", draft.get("unconfirmed") instanceof List<?> list ? List.copyOf(list) : List.of());
        List<Map<String, Object>> facts = keepGrounded(draft.get("facts"), pages);
        List<Map<String, Object>> reactions = keepGrounded(draft.get("reactions"), pages);
        out.put("facts", facts);
        out.put("reactions", reactions);
        List<Map<String, String>> sources = new ArrayList<>();
        for (Map<String, Object> fact : facts) {
            addSource(sources, str(fact.get("sourceTitle")), str(fact.get("sourceUrl")));
        }
        for (Map<String, Object> reaction : reactions) {
            addSource(sources, str(reaction.get("sourceTitle")), str(reaction.get("sourceUrl")));
        }
        if (sources.isEmpty()) {
            for (Page page : pages.values()) {
                sources.add(Map.of(
                        "title", page.title() == null ? "" : page.title(),
                        "url", page.url()));
            }
        }
        out.put("sources", sources);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> keepGrounded(Object raw, Map<String, Page> pages) {
        List<Map<String, Object>> kept = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return kept;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((k, v) -> row.put(String.valueOf(k), v));
            String url = str(row.get("sourceUrl"));
            String span = str(row.get("sourceSpan"));
            String text = str(row.get("text"));
            if (url.isBlank() || span.isBlank() || text.isBlank()) {
                continue;
            }
            Page page = pages.get(normalizeUrl(url));
            if (page == null) {
                for (Page candidate : pages.values()) {
                    if (normalizeUrl(candidate.url()).equals(normalizeUrl(url))) {
                        page = candidate;
                        break;
                    }
                }
            }
            if (page == null) {
                continue;
            }
            if (!NewsBriefService.normalize(page.text()).contains(NewsBriefService.normalize(span))) {
                continue;
            }
            row.put("sourceUrl", page.url());
            if (str(row.get("sourceTitle")).isBlank()) {
                row.put("sourceTitle", page.title() == null ? "" : page.title());
            }
            kept.add(row);
        }
        return kept;
    }

    private static void addSource(List<Map<String, String>> sources, String title, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String norm = normalizeUrl(url);
        for (Map<String, String> existing : sources) {
            if (normalizeUrl(existing.get("url")).equals(norm)) {
                return;
            }
        }
        sources.add(Map.of("title", title == null ? "" : title, "url", url));
    }

    static Map<String, Object> heuristic(Map<String, Page> pages, String headline) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whatHappened", headline == null ? "" : headline);
        out.put("whyItMatters", "");
        out.put("context", "");
        out.put("unconfirmed", List.of());
        List<Map<String, Object>> facts = new ArrayList<>();
        List<Map<String, String>> sources = new ArrayList<>();
        int i = 1;
        for (Page page : pages.values()) {
            String sentence = firstSentence(page.text());
            if (sentence.length() < 40) {
                continue;
            }
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("id", "vr" + i);
            fact.put("text", sentence);
            fact.put("sourceUrl", page.url());
            fact.put("sourceTitle", page.title() == null ? "" : page.title());
            fact.put("sourceSpan", sentence);
            facts.add(fact);
            sources.add(Map.of(
                    "title", page.title() == null ? "" : page.title(),
                    "url", page.url()));
            i++;
            if (facts.size() >= 4) {
                break;
            }
        }
        out.put("facts", facts);
        out.put("reactions", List.of());
        out.put("sources", sources);
        return out;
    }

    static boolean hasGroundedFacts(Map<String, Object> summary) {
        if (summary == null) {
            return false;
        }
        Object facts = summary.get("facts");
        return facts instanceof List<?> list && list.size() >= 2;
    }

    static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        int end = cleaned.indexOf('.');
        if (end < 40) {
            end = Math.min(220, cleaned.length());
        } else {
            end = Math.min(end + 1, cleaned.length());
        }
        return cleaned.substring(0, end).trim();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Page(String url, String title, String text) {
    }
}
