package com.gyanwire.actionplan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.news.NewsBrief;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ActionPlanStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ActionPlanStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public NewsBrief findBrief(String urlHash) {
        if (urlHash == null || urlHash.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbc.query(
                    "SELECT brief_json::text AS brief FROM news_briefs WHERE url_hash = ?",
                    (rs, i) -> Map.of("brief", rs.getString("brief")),
                    urlHash);
            if (rows.isEmpty()) {
                return null;
            }
            return fromBriefJson(String.valueOf(rows.get(0).get("brief")));
        } catch (Exception ex) {
            return null;
        }
    }

    public void saveBrief(String urlHash, NewsBrief brief) {
        if (urlHash == null || brief == null) {
            return;
        }
        try {
            UUID id = brief.newsId() == null || brief.newsId().isBlank()
                    ? UUID.randomUUID()
                    : UUID.fromString(brief.newsId());
            jdbc.update("""
                    INSERT INTO news_briefs (id, url_hash, headline, publisher_url, brief_json, created_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, now())
                    ON CONFLICT (url_hash) DO UPDATE SET
                        headline = EXCLUDED.headline,
                        publisher_url = EXCLUDED.publisher_url,
                        brief_json = EXCLUDED.brief_json
                    """,
                    id,
                    urlHash,
                    brief.headline(),
                    brief.publisherUrl(),
                    mapper.writeValueAsString(brief.toMap()));
        } catch (Exception ignored) {
            // Cache miss is fine; generation still works.
        }
    }

    public Map<String, Object> findIdeaCache(String urlHash) {
        if (urlHash == null || urlHash.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbc.query(
                    "SELECT ideas_json::text AS payload FROM news_idea_cache WHERE url_hash = ?",
                    (rs, i) -> Map.of("payload", rs.getString("payload")),
                    urlHash);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> payload = mapper.readValue(
                    String.valueOf(rows.get(0).get("payload")),
                    new TypeReference<>() {});
            if (!(payload.get("ideas") instanceof List<?> ideas) || ideas.isEmpty()) {
                return null;
            }
            return payload;
        } catch (Exception ex) {
            return null;
        }
    }

    public void saveIdeaCache(String urlHash, Map<String, Object> payload) {
        if (urlHash == null || urlHash.isBlank() || payload == null) {
            return;
        }
        if (!(payload.get("ideas") instanceof List<?> ideas) || ideas.isEmpty()) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO news_idea_cache (id, url_hash, ideas_json, created_at)
                    VALUES (?, ?, ?::jsonb, now())
                    ON CONFLICT (url_hash) DO UPDATE SET
                        ideas_json = EXCLUDED.ideas_json,
                        created_at = now()
                    """,
                    UUID.randomUUID(),
                    urlHash,
                    mapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // Cache write is best-effort.
        }
    }

    public UUID savePlan(UUID userId, String newsUrlHash, Map<String, Object> plan) {
        UUID id = plan.get("planId") instanceof String s && !s.isBlank()
                ? UUID.fromString(s)
                : UUID.randomUUID();
        plan.put("planId", id.toString());
        try {
            boolean lintPassed = plan.get("lint") instanceof Map<?, ?> lint
                    && Boolean.TRUE.equals(lint.get("passed"));
            jdbc.update("""
                    INSERT INTO action_plans (
                        id, user_id, news_url_hash, type, format, label, plan_json, lint_passed, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                    ON CONFLICT (id) DO UPDATE SET
                        plan_json = EXCLUDED.plan_json,
                        lint_passed = EXCLUDED.lint_passed,
                        label = EXCLUDED.label
                    """,
                    id,
                    userId,
                    newsUrlHash,
                    String.valueOf(plan.getOrDefault("type", "social_media")),
                    String.valueOf(plan.getOrDefault("format", "short_vertical")),
                    String.valueOf(plan.getOrDefault("label", "")),
                    mapper.writeValueAsString(plan),
                    lintPassed);
            syncSteps(id, plan);
            return id;
        } catch (Exception ex) {
            return id;
        }
    }

    public Map<String, Object> findPlan(UUID id, UUID userId) {
        try {
            List<Map<String, Object>> rows = jdbc.query("""
                    SELECT plan_json::text AS plan, user_id
                    FROM action_plans
                    WHERE id = ? AND deleted_at IS NULL
                    """, (rs, i) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("plan", rs.getString("plan"));
                Object uid = rs.getObject("user_id");
                row.put("userId", uid == null ? null : uid.toString());
                return row;
            }, id);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            if (userId != null && row.get("userId") != null && !userId.toString().equals(row.get("userId"))) {
                return null;
            }
            Map<String, Object> plan = mapper.readValue(String.valueOf(row.get("plan")), new TypeReference<>() {});
            attachDone(id, plan);
            return plan;
        } catch (Exception ex) {
            return null;
        }
    }

    public List<Map<String, Object>> listMine(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return jdbc.query("""
                    SELECT id, label, format, type, lint_passed, created_at
                    FROM action_plans
                    WHERE user_id = ? AND deleted_at IS NULL
                    ORDER BY created_at DESC
                    LIMIT 50
                    """, (rs, i) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("planId", rs.getObject("id").toString());
                row.put("label", rs.getString("label"));
                row.put("format", rs.getString("format"));
                row.put("type", rs.getString("type"));
                row.put("lintPassed", rs.getBoolean("lint_passed"));
                row.put("createdAt", rs.getTimestamp("created_at") == null
                        ? null
                        : rs.getTimestamp("created_at").toInstant().toString());
                return row;
            }, userId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void saveTiming(UUID userId, UUID planId, String stepKey, String toolId, int minutes) {
        if (userId == null || planId == null || stepKey == null || stepKey.isBlank()) {
            return;
        }
        jdbc.update("""
                INSERT INTO step_timings (id, user_id, plan_id, step_key, tool_id, minutes_actual)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, planId, stepKey, toolId, minutes);
    }

    public void setStepDone(UUID planId, UUID userId, String stepKey, boolean done) {
        if (planId == null || stepKey == null || stepKey.isBlank()) {
            return;
        }
        try {
            if (userId != null) {
                Integer owned = jdbc.queryForObject(
                        "SELECT count(*) FROM action_plans WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                        Integer.class,
                        planId,
                        userId);
                if (owned == null || owned == 0) {
                    return;
                }
            }
            if (done) {
                jdbc.update("""
                        INSERT INTO action_plan_steps (id, plan_id, step_key, done_at)
                        VALUES (?, ?, ?, now())
                        ON CONFLICT (plan_id, step_key) DO UPDATE SET done_at = now()
                        """, UUID.randomUUID(), planId, stepKey);
            } else {
                jdbc.update(
                        "UPDATE action_plan_steps SET done_at = NULL WHERE plan_id = ? AND step_key = ?",
                        planId,
                        stepKey);
            }
        } catch (Exception ignored) {
            // Tick persistence is best-effort when table missing in tests.
        }
    }

    @SuppressWarnings("unchecked")
    private void syncSteps(UUID planId, Map<String, Object> plan) {
        Object raw = plan.get("steps");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> step)) {
                continue;
            }
            String key = String.valueOf(step.get("id"));
            try {
                jdbc.update("""
                        INSERT INTO action_plan_steps (id, plan_id, step_key, done_at)
                        VALUES (?, ?, ?, NULL)
                        ON CONFLICT (plan_id, step_key) DO NOTHING
                        """, UUID.randomUUID(), planId, key);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void attachDone(UUID planId, Map<String, Object> plan) {
        try {
            List<String> done = jdbc.query(
                    "SELECT step_key FROM action_plan_steps WHERE plan_id = ? AND done_at IS NOT NULL",
                    (rs, i) -> rs.getString(1),
                    planId);
            if (!(plan.get("steps") instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> step) {
                    ((Map<String, Object>) step).put("done", done.contains(String.valueOf(step.get("id"))));
                }
            }
        } catch (Exception ignored) {
            // no ticks
        }
    }

    private NewsBrief fromBriefJson(String json) throws Exception {
        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        return NewsBrief.fromMap(map);
    }
}
