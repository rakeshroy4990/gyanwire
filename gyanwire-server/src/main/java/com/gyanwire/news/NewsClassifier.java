package com.gyanwire.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.LlmService;
import com.gyanwire.llm.ModelTier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NewsClassifier {

    private final LlmService llm;
    private final NewsProperties properties;
    private final ObjectMapper mapper;

    public NewsClassifier(
            @Qualifier("tieredLlmService") LlmService llm,
            NewsProperties properties,
            ObjectMapper mapper
    ) {
        this.llm = llm;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void applyRules(NewsRow row, String snippet, Instant now) {
        String blob = (row.title() == null ? "" : row.title()) + " " + (snippet == null ? "" : snippet);
        String signal = SignalRules.detect(blob);
        int specificity = Math.max(SignalRules.specificity(row.title()), SignalRules.specificity(snippet));
        int india = Math.max(SignalRules.indiaRelevance(row.title()), SignalRules.indiaRelevance(snippet));
        fill(row, signal, specificity, india, SignalRules.actionability(signal), now, true, SignalRules.whyIdea(signal, row.industry()));
    }

    public void enrich(List<NewsRow> rows, Map<UUID, String> snippets, Instant now) {
        if (rows == null || rows.isEmpty() || !llmEnabled()) {
            return;
        }
        int window = properties.getRank().windowDays()[0];
        List<NewsRow> batch = new java.util.ArrayList<>();
        for (NewsRow row : rows) {
            if (row.dateEstimated() || row.id() == null || "other".equals(row.signalType())) {
                continue;
            }
            long age = row.publishedAt() == null ? 999 : Duration.between(row.publishedAt(), now).toDays();
            if (age <= window) {
                batch.add(row);
            }
            if (batch.size() == 20) {
                classifyBatch(batch, snippets, now);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            classifyBatch(batch, snippets, now);
        }
    }

    private void classifyBatch(List<NewsRow> batch, Map<UUID, String> snippets, Instant now) {
        try {
            ArrayNode items = mapper.createArrayNode();
            for (int i = 0; i < batch.size(); i++) {
                NewsRow row = batch.get(i);
                ObjectNode node = items.addObject();
                node.put("index", i);
                node.put("title", row.title());
                node.put("summary", row.summary());
                String snippet = snippets == null ? "" : snippets.getOrDefault(row.id(), "");
                if (!snippet.isBlank()) {
                    node.put("snippet", snippet);
                }
                node.put("industry", row.industry());
            }
            ObjectNode input = mapper.createObjectNode();
            input.set("items", items);
            String system = com.gyanwire.research.engine.LlmService.prompt("news-classify.v1.txt");
            LlmRequest request = new LlmRequest(
                    "news_classify", ModelTier.LIGHT, system, mapper.writeValueAsString(input),
                    null, "system", "low", 1200);
            LlmResult result = llm.callValidated(request, json -> valid(json, batch.size()));
            if (!result.ok() || result.json() == null) {
                return;
            }
            for (JsonNode item : result.json().path("items")) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= batch.size()) {
                    continue;
                }
                NewsRow row = batch.get(index);
                String signal = item.path("signal_type").asText("other");
                if (!NewsTexts.SIGNALS.contains(signal)) {
                    signal = row.signalType();
                }
                fill(row, signal,
                        item.path("specificity").asInt(row.specificity()),
                        item.path("india_relevance").asInt(row.indiaRelevance()),
                        item.path("actionability").asInt(SignalRules.actionability(signal)),
                        now,
                        false,
                        guardWhy(item.path("why_idea").asText(""), row.industry()));
            }
        } catch (Exception ignored) {
            // Rule scores already stored stay in place.
        }
    }

    private void fill(NewsRow row, String signal, int specificity, int india, int action, Instant now, boolean rulesOnly, String why) {
        OpportunityScorer.Parts parts = OpportunityScorer.score(
                properties.getRank(), signal, specificity, india, action, row.publishedAt(), now, rulesOnly);
        row.signalType(signal);
        row.specificity(Math.max(0, Math.min(100, specificity)));
        row.indiaRelevance(Math.max(0, Math.min(100, india)));
        row.opportunityScore(parts.total());
        row.scoredBy(parts.scoredBy());
        row.whyIdea(why == null ? "" : why.trim());
        row.summary(NewsTexts.ownSummary(signal, row.domain()));
    }

    private boolean llmEnabled() {
        return properties.getClassify().isLlm() && llm.stageEnabled("news_classify");
    }

    private static boolean valid(JsonNode json, int size) {
        if (json == null || !json.path("items").isArray() || json.path("items").size() != size) {
            return false;
        }
        for (JsonNode item : json.path("items")) {
            if (!NewsTexts.SIGNALS.contains(item.path("signal_type").asText())) {
                return false;
            }
            if (item.path("why_idea").asText("").isBlank()) {
                return false;
            }
        }
        return true;
    }

    static String guardWhy(String why, String industry) {
        String line = why == null ? "" : why.replaceAll("\\s+", " ").trim();
        if (line.length() > 220) {
            line = line.substring(0, 220).trim();
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if ("Medical".equals(industry) && !lower.contains("education") && !lower.contains("admin") && !lower.contains("logistic")) {
            line = (line + " Education, admin, or logistics only.").trim();
        }
        if ("Share Market".equals(industry) && !lower.contains("education") && !lower.contains("tool")) {
            line = (line + " Education or tools only.").trim();
        }
        return line;
    }
}
