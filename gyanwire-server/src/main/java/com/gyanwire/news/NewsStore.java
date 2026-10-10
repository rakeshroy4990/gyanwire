package com.gyanwire.news;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class NewsStore {

    public record SourceRow(
            UUID id,
            String industry,
            String sub,
            String kind,
            String name,
            String urlTemplate,
            String region,
            double weight,
            boolean enabled,
            String etag,
            String lastModified,
            int consecutiveFailures
    ) {
    }

    public record PackRow(UUID id, String industry, String sub, String signalType, String query, int windowDays) {
    }

    private final JdbcTemplate jdbc;

    public NewsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> industries() {
        return jdbc.query("SELECT DISTINCT industry FROM news_sources ORDER BY industry",
                (rs, i) -> rs.getString(1));
    }

    public List<SourceRow> enabledSources(String industry, int max) {
        String sql = """
                SELECT id, industry, sub, kind, name, url_template, region, weight, enabled, etag, last_modified, consecutive_failures
                FROM news_sources
                WHERE industry = ? AND enabled = TRUE
                ORDER BY weight DESC, name
                """;
        if (max > 0) {
            sql += " LIMIT " + max;
        }
        return jdbc.query(sql, (rs, i) -> source(rs), industry);
    }

    public void markSourceOk(UUID id, String etag, String lastModified) {
        jdbc.update("""
                UPDATE news_sources
                SET last_ok_at = now(), last_error = NULL, consecutive_failures = 0, etag = ?, last_modified = ?
                WHERE id = ?
                """, etag, lastModified, id);
    }

    public void markSourceError(UUID id, String error) {
        jdbc.update("""
                UPDATE news_sources
                SET last_error = ?, consecutive_failures = consecutive_failures + 1
                WHERE id = ?
                """, trim(error, 500), id);
    }

    public PackRow claimPack(String industry, String sub) {
        List<PackRow> rows = jdbc.query("""
                UPDATE news_query_packs SET last_used_at = now()
                WHERE id = (
                    SELECT id FROM news_query_packs
                    WHERE industry = ? AND enabled = TRUE
                      AND (?::text IS NULL OR sub IS NULL OR sub = ?)
                    ORDER BY CASE WHEN sub IS NOT NULL AND sub = ? THEN 0 WHEN sub IS NULL THEN 1 ELSE 2 END,
                             last_used_at ASC NULLS FIRST
                    LIMIT 1
                )
                RETURNING id, industry, sub, signal_type, query, window_days
                """, (rs, i) -> new PackRow(
                rs.getObject("id", UUID.class),
                rs.getString("industry"),
                rs.getString("sub"),
                rs.getString("signal_type"),
                rs.getString("query"),
                rs.getInt("window_days")
        ), industry, sub, sub, sub);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Instant newestFirstSeen(String industry) {
        List<Instant> rows = jdbc.query("""
                SELECT max(first_seen_at) FROM news_items WHERE industry = ? AND sample = FALSE
                """, (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), industry);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    public boolean hasRealRows(String industry) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM news_items WHERE industry = ? AND sample = FALSE",
                Integer.class,
                industry);
        return count != null && count > 0;
    }

    public List<NewsRow> recent(String industry, Instant since) {
        return jdbc.query("""
                SELECT i.*, s.name AS source_name, s.weight AS source_weight
                FROM news_items i
                LEFT JOIN news_sources s ON s.id = i.source_id
                WHERE i.industry = ? AND i.published_at >= ?
                """, (rs, rowNum) -> mapRow(rs), industry, Timestamp.from(since));
    }

    public List<NewsRow> eligible(String industry, String sub, Instant since, boolean sample) {
        if (sub == null || sub.isBlank()) {
            return jdbc.query("""
                    SELECT i.*, s.name AS source_name, s.weight AS source_weight
                    FROM news_items i
                    LEFT JOIN news_sources s ON s.id = i.source_id
                    WHERE i.industry = ? AND i.sample = ? AND i.published_at >= ?
                    ORDER BY i.published_at DESC
                    LIMIT 80
                    """, (rs, rowNum) -> mapRow(rs), industry, sample, Timestamp.from(since));
        }
        return jdbc.query("""
                SELECT i.*, s.name AS source_name, s.weight AS source_weight
                FROM news_items i
                LEFT JOIN news_sources s ON s.id = i.source_id
                WHERE i.industry = ? AND i.sample = ? AND i.published_at >= ?
                  AND (i.sub IS NULL OR i.sub = ?)
                ORDER BY i.published_at DESC
                LIMIT 80
                """, (rs, rowNum) -> mapRow(rs), industry, sample, Timestamp.from(since), sub);
    }

    public boolean insert(NewsRow row) {
        UUID id = row.id() == null ? UUID.randomUUID() : row.id();
        row.id(id);
        int written = jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO news_items (
                        id, industry, sub, title, url, domain, resolved, source_id, published_at, date_estimated,
                        summary, signal_type, india_relevance, specificity, opportunity_score, why_idea,
                        title_hash, simhash, also_covered_by, sample, scored_by, language
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (url) DO NOTHING
                    """);
            ps.setObject(1, id);
            ps.setString(2, row.industry());
            ps.setString(3, row.sub());
            ps.setString(4, row.title());
            ps.setString(5, row.url());
            ps.setString(6, row.domain());
            ps.setBoolean(7, row.resolved());
            ps.setObject(8, row.sourceId());
            ps.setTimestamp(9, Timestamp.from(row.publishedAt()));
            ps.setBoolean(10, row.dateEstimated());
            ps.setString(11, row.summary());
            ps.setString(12, row.signalType());
            ps.setInt(13, row.indiaRelevance());
            ps.setInt(14, row.specificity());
            ps.setInt(15, row.opportunityScore());
            ps.setString(16, row.whyIdea());
            ps.setString(17, row.titleHash());
            ps.setLong(18, row.simhash());
            ps.setArray(19, textArray(connection, row.alsoCoveredBy()));
            ps.setBoolean(20, row.sample());
            ps.setString(21, row.scoredBy());
            ps.setString(22, row.language());
            return ps;
        });
        return written > 0;
    }

    public void updateScore(NewsRow row) {
        jdbc.update("""
                UPDATE news_items
                SET signal_type = ?, india_relevance = ?, specificity = ?, opportunity_score = ?,
                    why_idea = ?, summary = ?, scored_by = ?
                WHERE id = ?
                """, row.signalType(), row.indiaRelevance(), row.specificity(), row.opportunityScore(),
                row.whyIdea(), row.summary(), row.scoredBy(), row.id());
    }

    public void appendCoveredBy(String url, String sourceName) {
        if (url == null || sourceName == null || sourceName.isBlank()) {
            return;
        }
        jdbc.update("""
                UPDATE news_items
                SET also_covered_by = (
                    SELECT ARRAY(SELECT DISTINCT unnest(coalesce(also_covered_by, '{}'::text[]) || ARRAY[?]::text[]))
                )
                WHERE url = ?
                """, sourceName, url);
    }

    public void logRun(UUID sourceId, String industry, int fetched, int inserted, int deduped, int dropped, String reasons, String error) {
        jdbc.update("""
                INSERT INTO news_ingest_runs (id, source_id, industry, fetched, inserted, deduped, dropped, drop_reasons, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), sourceId, industry, fetched, inserted, deduped, dropped, trim(reasons, 500), trim(error, 500));
    }

    public void saveFeedback(UUID itemId, UUID userId, String anonHash, int vote, String reason, String industry, String signalType) {
        jdbc.update("""
                INSERT INTO news_feedback (id, item_id, user_id, anon_hash, vote, reason, industry, signal_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), itemId, userId, anonHash, vote, reason, industry, signalType);
    }

    public List<java.util.Map<String, Object>> sourceStatus() {
        return jdbc.query("""
                SELECT industry, name, enabled, last_ok_at, last_error, consecutive_failures,
                       (SELECT count(*) FROM news_items i WHERE i.source_id = news_sources.id AND i.first_seen_at >= now() - interval '1 day') AS items_day
                FROM news_sources
                ORDER BY industry, weight DESC
                """, (rs, i) -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("industry", rs.getString("industry"));
            row.put("name", rs.getString("name"));
            row.put("enabled", rs.getBoolean("enabled"));
            row.put("lastOkAt", rs.getTimestamp("last_ok_at") == null ? null : rs.getTimestamp("last_ok_at").toInstant().toString());
            row.put("lastError", rs.getString("last_error"));
            row.put("consecutiveFailures", rs.getInt("consecutive_failures"));
            row.put("itemsDay", rs.getInt("items_day"));
            return row;
        });
    }

    public List<java.util.Map<String, Object>> industryQuality(int freshDays) {
        return jdbc.query("""
                SELECT industry,
                       count(*) FILTER (WHERE sample = FALSE) AS items,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM (now() - published_at)) / 86400)
                         FILTER (WHERE sample = FALSE AND date_estimated = FALSE) AS median_age_days,
                       count(*) FILTER (WHERE sample = FALSE AND date_estimated = FALSE AND published_at >= now() - (? * interval '1 day')) AS fresh_items
                FROM news_items
                GROUP BY industry
                ORDER BY industry
                """, (rs, i) -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            int items = rs.getInt("items");
            int fresh = rs.getInt("fresh_items");
            row.put("industry", rs.getString("industry"));
            row.put("items", items);
            row.put("medianAgeDays", rs.getObject("median_age_days"));
            row.put("freshPct", items == 0 ? 0 : Math.round(fresh * 1000.0 / items) / 10.0);
            return row;
        }, freshDays);
    }

    public void preferSource(UUID id, UUID sourceId) {
        if (id == null || sourceId == null) {
            return;
        }
        jdbc.update("UPDATE news_items SET source_id = ? WHERE id = ?", sourceId, id);
    }

    public List<java.util.Map<String, Object>> feedbackBySignal() {
        return jdbc.query("""
                SELECT industry, signal_type,
                       count(*) FILTER (WHERE vote > 0) AS yes_votes,
                       count(*) FILTER (WHERE vote < 0 AND coalesce(reason, '') <> 'not_relevant') AS no_votes,
                       count(*) FILTER (WHERE reason = 'not_relevant') AS not_relevant
                FROM news_feedback
                WHERE created_at >= now() - interval '30 days'
                GROUP BY industry, signal_type
                ORDER BY industry, signal_type
                """, (rs, i) -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("industry", rs.getString("industry"));
            row.put("signalType", rs.getString("signal_type"));
            row.put("yes", rs.getInt("yes_votes"));
            row.put("no", rs.getInt("no_votes"));
            row.put("notRelevant", rs.getInt("not_relevant"));
            return row;
        });
    }

    private static SourceRow source(ResultSet rs) throws SQLException {
        return new SourceRow(
                rs.getObject("id", UUID.class),
                rs.getString("industry"),
                rs.getString("sub"),
                rs.getString("kind"),
                rs.getString("name"),
                rs.getString("url_template"),
                rs.getString("region"),
                rs.getDouble("weight"),
                rs.getBoolean("enabled"),
                rs.getString("etag"),
                rs.getString("last_modified"),
                rs.getInt("consecutive_failures")
        );
    }

    private static NewsRow mapRow(ResultSet rs) throws SQLException {
        Array covered = rs.getArray("also_covered_by");
        List<String> names = new ArrayList<>();
        if (covered != null && covered.getArray() instanceof String[] values) {
            names.addAll(List.of(values));
        }
        return new NewsRow()
                .id(rs.getObject("id", UUID.class))
                .industry(rs.getString("industry"))
                .sub(rs.getString("sub"))
                .title(rs.getString("title"))
                .url(rs.getString("url"))
                .domain(rs.getString("domain"))
                .resolved(rs.getBoolean("resolved"))
                .sourceId(rs.getObject("source_id", UUID.class))
                .sourceName(rs.getString("source_name"))
                .sourceWeight(rs.getDouble("source_weight"))
                .publishedAt(rs.getTimestamp("published_at").toInstant())
                .dateEstimated(rs.getBoolean("date_estimated"))
                .firstSeenAt(rs.getTimestamp("first_seen_at").toInstant())
                .summary(rs.getString("summary"))
                .signalType(rs.getString("signal_type"))
                .indiaRelevance(rs.getInt("india_relevance"))
                .specificity(rs.getInt("specificity"))
                .opportunityScore(rs.getInt("opportunity_score"))
                .whyIdea(rs.getString("why_idea"))
                .titleHash(rs.getString("title_hash"))
                .simhash(rs.getLong("simhash"))
                .alsoCoveredBy(names)
                .sample(rs.getBoolean("sample"))
                .scoredBy(rs.getString("scored_by"))
                .language(rs.getString("language"));
    }

    private static Array textArray(java.sql.Connection connection, List<String> items) throws SQLException {
        List<String> safe = items == null ? List.of() : items;
        return connection.createArrayOf("text", safe.toArray(String[]::new));
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
