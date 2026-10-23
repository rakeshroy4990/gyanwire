package com.gyanwire.news.github;

import com.gyanwire.news.NewsRow;
import com.gyanwire.news.NewsTexts;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Maps a GitHub repo into a news_items row so Idea reuse works unchanged. */
public final class GitHubRepoMapper {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private GitHubRepoMapper() {
    }

    public static NewsRow toNewsRow(
            GitHubRepo repo,
            String industry,
            String sub,
            UUID sourceId,
            String sourceName,
            double sourceWeight,
            Instant now
    ) {
        if (repo == null || !repo.usable()) {
            return null;
        }
        Instant when = now == null ? Instant.now() : now;
        String description = blankTo(repo.description(), "Popular open-source project on GitHub.");
        String pushDay = repo.pushedAt() == null ? "unknown" : DAY.format(repo.pushedAt());
        String licence = blankTo(repo.licence(), "unknown");
        String language = blankTo(repo.language(), "unknown");
        String summary = String.format(Locale.ROOT,
                "%s Open-source GitHub project with %s stars and %s forks. "
                        + "Language: %s. Licence: %s. Last push: %s.",
                description,
                formatCount(repo.stars()),
                formatCount(repo.forks()),
                language,
                licence,
                pushDay);
        int score = opportunityScore(repo.stars());
        String title = repo.fullName().trim();
        return new NewsRow()
                .industry(industry)
                .sub(sub)
                .title(title)
                .url(repo.htmlUrl())
                .domain("github.com")
                .resolved(true)
                .sourceId(sourceId)
                .sourceName(sourceName == null || sourceName.isBlank() ? "GitHub" : sourceName)
                .sourceWeight(sourceWeight <= 0 ? 1.5 : sourceWeight)
                .publishedAt(when)
                .dateEstimated(false)
                .summary(summary)
                .signalType("stars")
                .indiaRelevance(40)
                .specificity(Math.min(90, 40 + (int) Math.min(50, repo.stars() / 5000)))
                .opportunityScore(score)
                .whyIdea(summary)
                .titleHash(NewsTexts.titleHash(title))
                .simhash(NewsTexts.simhash(title))
                .language("en")
                .scoredBy("github");
    }

    static int opportunityScore(long stars) {
        if (stars >= 100_000) {
            return 95;
        }
        if (stars >= 50_000) {
            return 88;
        }
        if (stars >= 20_000) {
            return 78;
        }
        if (stars >= 10_000) {
            return 70;
        }
        if (stars >= 5_000) {
            return 62;
        }
        if (stars >= 1_000) {
            return 55;
        }
        return 45;
    }

    static String formatCount(long value) {
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    /**
     * Facts already stored on older rows, without the stock closer that
     * only repeated "famous library" and "India-first".
     */
    public static String projectDetail(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String text = summary.replaceAll("\\s+", " ").trim();
        int cut = indexOfIgnoreCase(text, "Famous library/framework/tool");
        if (cut > 0) {
            text = text.substring(0, cut).trim();
        }
        return text;
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
