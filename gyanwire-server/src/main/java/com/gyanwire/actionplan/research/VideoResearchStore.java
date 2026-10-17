package com.gyanwire.actionplan.research;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class VideoResearchStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public VideoResearchStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public VideoResearchPack find(String urlHash) {
        if (urlHash == null || urlHash.isBlank()) {
            return null;
        }
        try {
            List<VideoResearchPack> rows = jdbc.query("""
                    SELECT url_hash, status, summary_json::text AS summary, error, updated_at
                    FROM video_research
                    WHERE url_hash = ?
                    """, (rs, i) -> {
                Map<String, Object> summary = null;
                String summaryJson = rs.getString("summary");
                if (summaryJson != null && !summaryJson.isBlank()) {
                    try {
                        summary = mapper.readValue(summaryJson, new TypeReference<>() {});
                    } catch (Exception ignored) {
                        summary = Map.of();
                    }
                }
                Timestamp updated = rs.getTimestamp("updated_at");
                return new VideoResearchPack(
                        rs.getString("url_hash"),
                        rs.getString("status"),
                        summary,
                        rs.getString("error"),
                        updated == null ? null : updated.toInstant());
            }, urlHash);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Returns true when this caller should run the research job.
     * Skips when a fresh ready or running row already exists.
     */
    public boolean claimRunning(String urlHash, UUID newsId, Duration ttl) {
        if (urlHash == null || urlHash.isBlank()) {
            return false;
        }
        VideoResearchPack existing = find(urlHash);
        if (existing != null && fresh(existing, ttl)) {
            if (existing.ready() || existing.running()) {
                return false;
            }
        }
        try {
            jdbc.update("""
                    INSERT INTO video_research (id, url_hash, news_id, status, summary_json, error, created_at, updated_at)
                    VALUES (?, ?, ?, 'running', NULL, NULL, now(), now())
                    ON CONFLICT (url_hash) DO UPDATE SET
                        news_id = COALESCE(EXCLUDED.news_id, video_research.news_id),
                        status = 'running',
                        summary_json = NULL,
                        error = NULL,
                        updated_at = now()
                    """,
                    UUID.randomUUID(),
                    urlHash,
                    newsId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void markReady(String urlHash, Map<String, Object> summary) {
        if (urlHash == null || urlHash.isBlank() || summary == null) {
            return;
        }
        try {
            jdbc.update("""
                    UPDATE video_research
                    SET status = 'ready', summary_json = ?::jsonb, error = NULL, updated_at = now()
                    WHERE url_hash = ?
                    """,
                    mapper.writeValueAsString(summary),
                    urlHash);
        } catch (Exception ignored) {
            // Best-effort cache write.
        }
    }

    public void markFailed(String urlHash, String error) {
        if (urlHash == null || urlHash.isBlank()) {
            return;
        }
        try {
            jdbc.update("""
                    UPDATE video_research
                    SET status = 'failed', error = ?, updated_at = now()
                    WHERE url_hash = ?
                    """,
                    error == null ? "Research failed" : error,
                    urlHash);
        } catch (Exception ignored) {
            // Best-effort cache write.
        }
    }

    public VideoResearchPack findByNewsId(UUID newsId) {
        if (newsId == null) {
            return null;
        }
        try {
            List<String> hashes = jdbc.query(
                    "SELECT url_hash FROM video_research WHERE news_id = ? ORDER BY updated_at DESC LIMIT 1",
                    (rs, i) -> rs.getString(1),
                    newsId);
            if (hashes.isEmpty()) {
                return null;
            }
            return find(hashes.get(0));
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean fresh(VideoResearchPack pack, Duration ttl) {
        if (pack == null || pack.updatedAt() == null || ttl == null) {
            return false;
        }
        return pack.updatedAt().isAfter(Instant.now().minus(ttl));
    }

    /** Test helper: write a row without claiming. */
    public void upsert(String urlHash, UUID newsId, String status, Map<String, Object> summary, String error) {
        try {
            String json = summary == null ? null : mapper.writeValueAsString(summary);
            jdbc.update("""
                    INSERT INTO video_research (id, url_hash, news_id, status, summary_json, error, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, now(), now())
                    ON CONFLICT (url_hash) DO UPDATE SET
                        news_id = EXCLUDED.news_id,
                        status = EXCLUDED.status,
                        summary_json = EXCLUDED.summary_json,
                        error = EXCLUDED.error,
                        updated_at = now()
                    """,
                    UUID.randomUUID(),
                    urlHash,
                    newsId,
                    status,
                    json,
                    error);
        } catch (Exception ignored) {
            // ignore in tests without table
        }
    }

    public Map<String, Object> emptySummary() {
        return new LinkedHashMap<>();
    }
}
