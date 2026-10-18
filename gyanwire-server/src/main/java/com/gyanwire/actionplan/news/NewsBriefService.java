package com.gyanwire.actionplan.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.ActionPlanStore;
import com.gyanwire.ideas.pipeline.IdeaStageClient;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NewsBriefService {

    private final ObjectMapper mapper;
    private final ObjectProvider<IdeaStageClient> stageClient;
    private final ActionPlanStore store;
    private final NewsResolveService resolve;

    public NewsBriefService(
            ObjectMapper mapper,
            ObjectProvider<IdeaStageClient> stageClient,
            ActionPlanStore store,
            NewsResolveService resolve
    ) {
        this.mapper = mapper;
        this.stageClient = stageClient;
        this.store = store;
        this.resolve = resolve;
    }

    public static final int IDEAS_SNIPPET_MIN = 80;

    public NewsBrief extract(
            String newsUrl,
            String headline,
            String source,
            String pastedText,
            UUID userId
    ) {
        String urlHash = urlHash(newsUrl);
        NewsBrief cached = store.findBrief(urlHash);
        if (cached != null && cached.usable()) {
            return cached;
        }

        ArticleText article;
        if (pastedText != null && pastedText.trim().length() >= NewsResolveService.MIN_CHARS) {
            article = resolve.fromPastedText(pastedText, headline, newsUrl);
        } else {
            article = resolve.resolve(newsUrl, headline, source);
        }
        if (article.empty()) {
            return NewsBrief.insufficient(headline, newsUrl);
        }

        NewsBrief brief = fromLlm(article, headline, userId);
        if (brief == null) {
            brief = heuristic(article, headline);
        }
        brief = verify(brief, article, headline);
        if (brief.usable()) {
            store.saveBrief(urlHash, brief);
        }
        return brief;
    }

    /**
     * Build a usable brief from a multi-source video-research pack when the
     * original publisher page cannot be read.
     */
    public NewsBrief fromVideoResearch(
            String headline,
            String publisherUrl,
            String publisher,
            Map<String, Object> research
    ) {
        if (research == null || research.isEmpty()) {
            return NewsBrief.insufficient(headline, publisherUrl);
        }
        List<NewsBrief.Fact> facts = new ArrayList<>();
        Object rawFacts = research.get("facts");
        if (rawFacts instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                Object rawText = row.get("text");
                String text = rawText == null ? "" : String.valueOf(rawText).trim();
                if (text.length() < 24) {
                    continue;
                }
                Object rawSpan = row.get("sourceSpan");
                String span = rawSpan == null ? "" : String.valueOf(rawSpan).trim();
                if (span.isBlank()) {
                    span = text;
                }
                facts.add(new NewsBrief.Fact("f" + (facts.size() + 1), trim(text, 240), trim(span, 240), "medium"));
                if (facts.size() >= 5) {
                    break;
                }
            }
        }
        String what = research.get("whatHappened") == null ? "" : String.valueOf(research.get("whatHappened")).trim();
        String why = research.get("whyItMatters") == null ? "" : String.valueOf(research.get("whyItMatters")).trim();
        if (facts.size() < 3 && what.length() >= 24) {
            facts.add(new NewsBrief.Fact("f" + (facts.size() + 1), trim(what, 240), trim(what, 240), "medium"));
        }
        if (facts.size() < 3 && why.length() >= 24) {
            facts.add(new NewsBrief.Fact("f" + (facts.size() + 1), trim(why, 240), trim(why, 240), "medium"));
        }
        if (facts.size() < 3) {
            String blob = ((headline == null ? "" : headline) + ". " + what + " " + why).replaceAll("\\s+", " ").trim();
            return localBrief(headline, publisherUrl, publisher, blob, null);
        }
        List<String> entities = new ArrayList<>();
        Object rawEntities = research.get("entities");
        if (rawEntities instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    entities.add(String.valueOf(item).trim());
                }
            }
        }
        if (entities.isEmpty()) {
            entities = guessEntities(headline, what + " " + why);
        }
        return new NewsBrief(
                UUID.randomUUID().toString(),
                headline == null ? "" : headline,
                publisherUrl == null ? "" : publisherUrl,
                publisher == null ? "" : publisher,
                null,
                entities,
                facts,
                List.of("builders", "creators"),
                null);
    }

    /**
     * Local NewsBrief for the ideas path. No LLM, not written to {@code news_briefs}.
     */
    public NewsBrief localBrief(
            String headline,
            String publisherUrl,
            String publisher,
            String text,
            String industry
    ) {
        ArticleText article = ArticleText.snippet(publisherUrl, publisher, text, IDEAS_SNIPPET_MIN);
        if (article.empty()) {
            return NewsBrief.insufficient(headline, publisherUrl);
        }
        NewsBrief brief = heuristic(article, headline);
        brief = padFacts(brief, article, headline);
        List<String> who = new ArrayList<>();
        if (industry != null && !industry.isBlank()) {
            who.add(industry.trim());
        }
        if (brief.whoItAffects() != null) {
            for (String row : brief.whoItAffects()) {
                if (row != null && !row.isBlank() && !who.contains(row)) {
                    who.add(row);
                }
            }
        }
        if (who.isEmpty()) {
            who = List.of("builders", "creators");
        }
        return new NewsBrief(
                brief.newsId(),
                brief.headline(),
                brief.publisherUrl(),
                brief.publisher(),
                brief.publishedAt(),
                brief.entities(),
                brief.facts(),
                who,
                brief.freshnessHours());
    }

    private static NewsBrief padFacts(NewsBrief brief, ArticleText article, String headline) {
        List<NewsBrief.Fact> facts = new ArrayList<>();
        if (brief.facts() != null) {
            facts.addAll(brief.facts());
        }
        if (facts.size() >= 3) {
            return brief;
        }
        String blob = ((headline == null ? "" : headline) + ". " + article.text()).replaceAll("\\s+", " ").trim();
        for (String part : blob.split("(?<=[.!?])\\s+|\\s*[;|]\\s+|\\s{2,}")) {
            String sentence = part.trim();
            if (sentence.length() < 24) {
                continue;
            }
            String span = trim(sentence, 240);
            boolean dup = facts.stream().anyMatch(f -> normalize(f.text()).equals(normalize(span)));
            if (dup) {
                continue;
            }
            facts.add(new NewsBrief.Fact("f" + (facts.size() + 1), span, span, "medium"));
            if (facts.size() >= 3) {
                break;
            }
        }
        while (facts.size() < 3 && blob.length() >= 24) {
            int start = Math.min(facts.size() * 80, Math.max(0, blob.length() - 80));
            String chunk = trim(blob.substring(start), 240);
            if (chunk.length() < 24) {
                break;
            }
            facts.add(new NewsBrief.Fact("f" + (facts.size() + 1), chunk, chunk, "medium"));
        }
        return new NewsBrief(
                brief.newsId(),
                brief.headline(),
                brief.publisherUrl(),
                brief.publisher(),
                brief.publishedAt(),
                brief.entities(),
                facts,
                brief.whoItAffects(),
                brief.freshnessHours());
    }

    private NewsBrief fromLlm(ArticleText article, String headline, UUID userId) {
        IdeaStageClient client = stageClient.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            String system = LlmService.prompt("news-brief.v1.txt");
            String user = mapper.writeValueAsString(Map.of(
                    "headline", headline == null ? "" : headline,
                    "articleText", article.text()));
            JsonNode node = client.complete(
                    userId, "news-brief.v1", "news-brief", system, user, ModelTier.LIGHT);
            if (node == null || !node.isObject()) {
                return null;
            }
            String err = validateShape(node);
            if (err != null) {
                node = client.complete(
                        userId,
                        "news-brief.v1",
                        "news-brief",
                        system,
                        user + "\nValidation error: " + err + "\nReturn JSON that fixes it.",
                        ModelTier.LIGHT);
                if (node == null || validateShape(node) != null) {
                    return null;
                }
            }
            return parse(node, article, headline);
        } catch (Exception ex) {
            return null;
        }
    }

    private NewsBrief heuristic(ArticleText article, String headline) {
        String text = article.text();
        List<String> sentences = sentences(text);
        List<NewsBrief.Fact> facts = new ArrayList<>();
        int i = 1;
        for (String sentence : sentences) {
            if (sentence.length() < 40) {
                continue;
            }
            facts.add(new NewsBrief.Fact("f" + i++, trim(sentence, 240), sentence, "medium"));
            if (facts.size() >= 5) {
                break;
            }
        }
        List<String> entities = guessEntities(headline, text);
        return new NewsBrief(
                UUID.randomUUID().toString(),
                headline == null ? "" : headline,
                article.publisherUrl(),
                article.publisher(),
                null,
                entities,
                facts,
                List.of("developers", "students"),
                null);
    }

    NewsBrief verify(NewsBrief brief, ArticleText article, String headline) {
        String corpus = normalize(article.text());
        String head = normalize(headline == null ? "" : headline);
        List<NewsBrief.Fact> keptFacts = new ArrayList<>();
        if (brief.facts() != null) {
            for (NewsBrief.Fact fact : brief.facts()) {
                String span = fact.sourceSpan() == null ? "" : fact.sourceSpan();
                if (span.isBlank()) {
                    continue;
                }
                if (!corpus.contains(normalize(span))) {
                    continue;
                }
                keptFacts.add(fact);
            }
        }
        List<String> keptEntities = new ArrayList<>();
        if (brief.entities() != null) {
            for (String entity : brief.entities()) {
                if (entity == null || entity.isBlank()) {
                    continue;
                }
                String n = normalize(entity);
                if (corpus.contains(n) || head.contains(n)) {
                    keptEntities.add(entity.trim());
                }
            }
        }
        Integer freshness = freshnessHours(brief.publishedAt());
        return new NewsBrief(
                brief.newsId() == null || brief.newsId().isBlank() ? UUID.randomUUID().toString() : brief.newsId(),
                brief.headline() == null || brief.headline().isBlank() ? (headline == null ? "" : headline) : brief.headline(),
                article.publisherUrl().isBlank() ? brief.publisherUrl() : article.publisherUrl(),
                article.publisher().isBlank() ? brief.publisher() : article.publisher(),
                brief.publishedAt(),
                keptEntities,
                keptFacts,
                brief.whoItAffects() == null ? List.of() : brief.whoItAffects(),
                freshness);
    }

    private NewsBrief parse(JsonNode node, ArticleText article, String headline) {
        List<String> entities = new ArrayList<>();
        if (node.path("entities").isArray()) {
            node.path("entities").forEach(n -> {
                if (!n.asText("").isBlank()) {
                    entities.add(n.asText().trim());
                }
            });
        }
        List<NewsBrief.Fact> facts = new ArrayList<>();
        if (node.path("facts").isArray()) {
            int i = 1;
            for (JsonNode f : node.path("facts")) {
                String id = f.path("id").asText("f" + i);
                facts.add(new NewsBrief.Fact(
                        id.isBlank() ? "f" + i : id,
                        f.path("text").asText("").trim(),
                        f.path("sourceSpan").asText("").trim(),
                        f.path("confidence").asText("medium").trim()));
                i++;
            }
        }
        List<String> who = new ArrayList<>();
        if (node.path("whoItAffects").isArray()) {
            node.path("whoItAffects").forEach(n -> {
                if (!n.asText("").isBlank()) {
                    who.add(n.asText().trim());
                }
            });
        }
        return new NewsBrief(
                UUID.randomUUID().toString(),
                headline == null ? "" : headline,
                article.publisherUrl(),
                article.publisher(),
                node.path("publishedAt").asText(null),
                entities,
                facts,
                who,
                null);
    }

    static String validateShape(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        if (!node.path("facts").isArray() || node.path("facts").size() < 3) {
            return "need >=3 facts";
        }
        return null;
    }

    public static String urlHash(String url) {
        String raw = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Integer freshnessHours(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            LocalDate day = LocalDate.parse(publishedAt.trim().substring(0, Math.min(10, publishedAt.trim().length())));
            long days = ChronoUnit.DAYS.between(day, LocalDate.now());
            return (int) Math.max(0, days * 24);
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("(?<=[.!?])\\s+")) {
            String s = part.trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<String> guessEntities(String headline, String text) {
        List<String> out = new ArrayList<>();
        String blob = (headline == null ? "" : headline) + " " + (text == null ? "" : text.substring(0, Math.min(500, text.length())));
        for (String token : blob.split("[\\s,.;:]+")) {
            if (token.length() >= 3 && Character.isUpperCase(token.charAt(0)) && !out.contains(token)) {
                out.add(token.replaceAll("[^A-Za-z0-9.]+$", ""));
            }
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max).trim();
    }
}
