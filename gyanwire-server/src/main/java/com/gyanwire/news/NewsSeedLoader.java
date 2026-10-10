package com.gyanwire.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NewsSeedLoader {

    private final NewsStore store;
    private final ObjectMapper mapper;
    private final NewsProperties properties;

    public NewsSeedLoader(NewsStore store, ObjectMapper mapper, NewsProperties properties) {
        this.store = store;
        this.mapper = mapper;
        this.properties = properties;
    }

    public int load(String industry, boolean prodProfile, boolean fetchFailed) {
        if (!SeedPolicy.shouldSeed(
                prodProfile,
                properties.getSeed().isAllowed(),
                store.hasRealRows(industry),
                fetchFailed,
                properties.getSeed().isOnEmpty())) {
            return 0;
        }
        try {
            JsonNode root = mapper.readTree(new ClassPathResource("news-seed.dev.json").getInputStream());
            int loaded = 0;
            for (JsonNode node : root) {
                if (!industry.equals(node.path("industry").asText())) {
                    continue;
                }
                Instant published = Instant.parse(node.path("publishedAt").asText());
                NewsRow row = new NewsRow()
                        .industry(industry)
                        .sub(node.path("sub").asText(null))
                        .title(node.path("title").asText())
                        .url(node.path("url").asText())
                        .domain(NewsTexts.domainOf(node.path("url").asText()))
                        .resolved(true)
                        .publishedAt(published)
                        .dateEstimated(false)
                        .summary(node.path("summary").asText())
                        .signalType(node.path("signalType").asText("other"))
                        .indiaRelevance(node.path("indiaRelevance").asInt(80))
                        .specificity(node.path("specificity").asInt(60))
                        .opportunityScore(node.path("opportunityScore").asInt(50))
                        .whyIdea(node.path("whyIdea").asText())
                        .titleHash(NewsTexts.titleHash(node.path("title").asText()))
                        .simhash(NewsTexts.simhash(node.path("title").asText()))
                        .sample(true)
                        .scoredBy("rules")
                        .language("en");
                if (node.path("sub").asText("").isBlank()) {
                    row.sub(null);
                }
                if (store.insert(row)) {
                    loaded++;
                }
            }
            return loaded;
        } catch (Exception ex) {
            return 0;
        }
    }
}
