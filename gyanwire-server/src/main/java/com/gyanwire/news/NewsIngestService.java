package com.gyanwire.news;

import com.gyanwire.research.engine.PublishedDates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NewsIngestService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestService.class);

    private final NewsStore store;
    private final FeedFetcher fetcher;
    private final RedirectResolver redirects;
    private final NewsClassifier classifier;
    private final NewsProperties properties;

    public NewsIngestService(
            NewsStore store,
            FeedFetcher fetcher,
            RedirectResolver redirects,
            NewsClassifier classifier,
            NewsProperties properties
    ) {
        this.store = store;
        this.fetcher = fetcher;
        this.redirects = redirects;
        this.classifier = classifier;
        this.properties = properties;
    }

    public boolean fresh(String industry, Instant now) {
        return FreshnessGate.shouldSkip(
                store.newestFirstSeen(industry),
                now,
                Duration.ofMinutes(Math.max(properties.getIngest().getPollIntervalMinutes(), 1)));
    }

    public IndustryFetchGate.Outcome run(String industry, String sub) {
        Instant now = Instant.now();
        List<NewsStore.SourceRow> sources = store.enabledSources(industry, properties.getIngest().getMaxSourcesPerIndustry());
        int[] failures = {0};
        SourceBatch.run(sources, source -> ingestSource(source, industry, sub, now), ex -> {
            failures[0]++;
            log.info("News source failed for {}: {}", industry, ex.getMessage());
        });
        warnStaleSources(sources);
        return failures[0] > 0 && failures[0] == sources.size()
                ? IndustryFetchGate.Outcome.failure()
                : IndustryFetchGate.Outcome.done();
    }

    private void ingestSource(NewsStore.SourceRow source, String industry, String sub, Instant now) {
        String url = source.urlTemplate();
        String itemSub = source.sub();
        if ("search_feed".equals(source.kind())) {
            NewsStore.PackRow pack = store.claimPack(industry, sub);
            if (pack == null) {
                store.logRun(source.id(), industry, 0, 0, 0, 1, "no_pack", null);
                return;
            }
            String query = pack.query();
            if (sub != null && !sub.isBlank() && (pack.sub() == null || pack.sub().isBlank())) {
                query = sub + " " + query;
            }
            url = NewsUrls.apply(source.urlTemplate(), query);
            itemSub = pack.sub() != null ? pack.sub() : sub;
        }
        FeedFetcher.Fetch fetch = fetcher.fetch(url, source.etag(), source.lastModified());
        if (fetch.error() != null) {
            store.markSourceError(source.id(), fetch.error());
            store.logRun(source.id(), industry, 0, 0, 0, 0, null, fetch.error());
            log.info("News fetch {} {}: {}", industry, source.name(), fetch.error());
            if (source.consecutiveFailures() + 1 >= properties.getAlert().getFailureStreak()) {
                log.warn("News source {} failed {} runs", source.name(), source.consecutiveFailures() + 1);
            }
            throw new IllegalStateException(fetch.error());
        }
        store.markSourceOk(source.id(), fetch.etag(), fetch.lastModified());
        if (fetch.notModified()) {
            store.logRun(source.id(), industry, 0, 0, 0, 0, null, null);
            return;
        }
        List<NewsRow> recent = new ArrayList<>(store.recent(industry, now.minus(Duration.ofDays(3))));
        int inserted = 0;
        int deduped = 0;
        int dropped = 0;
        Map<String, Integer> reasons = new HashMap<>();
        List<NewsRow> forModel = new ArrayList<>();
        Map<UUID, String> snippets = new HashMap<>();
        for (FeedParser.Item item : fetch.items()) {
            String title = NewsTexts.cleanTitle(item.title());
            if (title.isBlank() || item.link() == null || item.link().isBlank()) {
                dropped++;
                reasons.merge("blank", 1, Integer::sum);
                continue;
            }
            if (!NewsTexts.keepLanguage(title)) {
                dropped++;
                reasons.merge("language", 1, Integer::sum);
                continue;
            }
            NewsRow near = nearOf(recent, title);
            if (near != null) {
                if (near.sourceWeight() >= source.weight()) {
                    store.appendCoveredBy(near.url(), source.name());
                } else {
                    store.appendCoveredBy(near.url(), near.sourceName());
                    store.preferSource(near.id(), source.id());
                    near.sourceId(source.id());
                    near.sourceName(source.name());
                    near.sourceWeight(source.weight());
                }
                deduped++;
                continue;
            }
            RedirectResolver.Resolved link = redirects.resolve(item.link());
            Instant published = PublishedDates.parse(item.publishedRaw());
            boolean estimated = published == null;
            if (estimated) {
                published = now;
            }
            NewsRow row = new NewsRow()
                    .industry(industry)
                    .sub(itemSub)
                    .title(title)
                    .url(link.url())
                    .domain(link.domain().isBlank() ? source.name() : link.domain())
                    .resolved(link.resolved())
                    .sourceId(source.id())
                    .sourceName(source.name())
                    .sourceWeight(source.weight())
                    .publishedAt(published)
                    .dateEstimated(estimated)
                    .titleHash(NewsTexts.titleHash(title))
                    .simhash(NewsTexts.simhash(title))
                    .language(NewsTexts.languageOf(title));
            classifier.applyRules(row, item.snippet(), now);
            if (!store.insert(row)) {
                store.appendCoveredBy(link.url(), source.name());
                deduped++;
                continue;
            }
            inserted++;
            recent.add(row);
            snippets.put(row.id(), item.snippet() == null ? "" : item.snippet());
            forModel.add(row);
        }
        classifier.enrich(forModel, snippets, now);
        for (NewsRow row : forModel) {
            if ("llm".equals(row.scoredBy())) {
                store.updateScore(row);
            }
        }
        store.logRun(source.id(), industry, fetch.items().size(), inserted, deduped, dropped, reasonText(reasons), null);
        log.info("News {} {}: fetched={} new={} deduped={} dropped={}",
                industry, source.name(), fetch.items().size(), inserted, deduped, dropped);
    }

    private static NewsRow nearOf(List<NewsRow> recent, String title) {
        for (NewsRow row : recent) {
            if (NewsTexts.nearDuplicate(row.title(), title)) {
                return row;
            }
        }
        return null;
    }

    private void warnStaleSources(List<NewsStore.SourceRow> sources) {
        for (NewsStore.SourceRow source : sources) {
            if (source.consecutiveFailures() >= properties.getAlert().getFailureStreak()) {
                log.warn("News source {} is on an error streak of {}", source.name(), source.consecutiveFailures());
            }
        }
    }

    private static String reasonText(Map<String, Integer> reasons) {
        if (reasons.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        reasons.forEach((key, count) -> {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(key).append(':').append(count);
        });
        return out.toString();
    }
}
