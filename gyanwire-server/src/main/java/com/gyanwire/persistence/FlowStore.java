package com.gyanwire.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class FlowStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public FlowStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> findProfile(UUID userId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM user_profiles WHERE user_id = ?",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("persona", rs.getString("persona"));
                    row.put("goal90d", rs.getString("goal_90d"));
                    row.put("capitalBand", rs.getString("capital_band"));
                    row.put("incomeBand", rs.getString("income_band"));
                    row.put("investPct", rs.getInt("invest_pct"));
                    row.put("hoursPerWeek", rs.getObject("hours_per_week"));
                    row.put("locationTier", rs.getString("location_tier"));
                    row.put("state", rs.getString("state"));
                    row.put("city", rs.getString("city"));
                    row.put("languages", array(rs.getArray("languages")));
                    row.put("industries", array(rs.getArray("industries")));
                    row.put("assets", array(rs.getArray("assets")));
                    row.put("riskAppetite", rs.getString("risk_appetite"));
                    row.put("constraints", array(rs.getArray("constraints")));
                    row.put("education", rs.getString("education"));
                    row.put("consentAt", rs.getTimestamp("consent_at") == null ? null : rs.getTimestamp("consent_at").toInstant().toString());
                    row.put("digestOptIn", columnExists(rs, "digest_opt_in") && rs.getBoolean("digest_opt_in"));
                    return row;
                },
                userId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> profile = rows.get(0);
        profile.put("skills", jdbc.query(
                "SELECT skill_tag, level FROM user_skills WHERE user_id = ?",
                (rs, i) -> Map.of("tag", rs.getString("skill_tag"), "level", rs.getInt("level")),
                userId));
        return profile;
    }

    public void saveProfile(UUID userId, Map<String, Object> body) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO user_profiles (
                        user_id, persona, goal_90d, capital_band, income_band, invest_pct, hours_per_week,
                        location_tier, state, city, languages, industries, assets, risk_appetite, constraints,
                        education, consent_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (user_id) DO UPDATE SET
                        persona = EXCLUDED.persona,
                        goal_90d = EXCLUDED.goal_90d,
                        capital_band = EXCLUDED.capital_band,
                        income_band = EXCLUDED.income_band,
                        invest_pct = EXCLUDED.invest_pct,
                        hours_per_week = EXCLUDED.hours_per_week,
                        location_tier = EXCLUDED.location_tier,
                        state = EXCLUDED.state,
                        city = EXCLUDED.city,
                        languages = EXCLUDED.languages,
                        industries = EXCLUDED.industries,
                        assets = EXCLUDED.assets,
                        risk_appetite = EXCLUDED.risk_appetite,
                        constraints = EXCLUDED.constraints,
                        education = EXCLUDED.education,
                        consent_at = now(),
                        updated_at = now()
                    """);
            ps.setObject(1, userId);
            ps.setString(2, str(body.get("persona")));
            ps.setString(3, str(body.get("goal90d")));
            ps.setString(4, str(body.get("capitalBand")));
            ps.setString(5, str(body.get("incomeBand")));
            ps.setInt(6, intVal(body.get("investPct"), 10));
            if (body.get("hoursPerWeek") == null) {
                ps.setObject(7, null);
            } else {
                ps.setInt(7, intVal(body.get("hoursPerWeek"), 5));
            }
            ps.setString(8, str(body.get("locationTier")));
            ps.setString(9, str(body.get("state")));
            ps.setString(10, str(body.get("city")));
            ps.setArray(11, textArray(connection, body.get("languages")));
            ps.setArray(12, textArray(connection, body.get("industries")));
            ps.setArray(13, textArray(connection, body.get("assets")));
            ps.setString(14, str(body.get("riskAppetite")));
            ps.setArray(15, textArray(connection, body.get("constraints")));
            ps.setString(16, str(body.get("education")));
            return ps;
        });
        jdbc.update("DELETE FROM user_skills WHERE user_id = ?", userId);
        Object skills = body.get("skills");
        if (skills instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> skill) {
                    jdbc.update(
                            "INSERT INTO user_skills (user_id, skill_tag, level) VALUES (?, ?, ?)",
                            userId,
                            str(skill.get("tag")),
                            intVal(skill.get("level"), 1));
                }
            }
        }
    }

    public void deleteProfile(UUID userId) {
        jdbc.update("DELETE FROM user_skills WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
    }

    public String findSignal(String urlHash) {
        List<String> rows = jdbc.query(
                "SELECT signal::text FROM news_signals WHERE url_hash = ?",
                (rs, i) -> rs.getString(1),
                urlHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveSignal(String urlHash, String json) {
        jdbc.update(
                "INSERT INTO news_signals (id, url_hash, signal) VALUES (?, ?, ?::jsonb) ON CONFLICT (url_hash) DO NOTHING",
                UUID.randomUUID(), urlHash, json);
    }

    public UUID findIdeaRun(String idempotencyKey) {
        List<UUID> rows = jdbc.query(
                "SELECT id FROM idea_runs WHERE idempotency_key = ?",
                (rs, i) -> rs.getObject(1, UUID.class),
                idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertIdeaRun(UUID id, UUID userId, String url, String urlHash, String title, String industry, String signals, String version, String key) {
        jdbc.update("""
                INSERT INTO idea_runs (id, user_id, url, url_hash, title, industry, signals, prompt_version, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, id, userId, url, urlHash, title, industry, signals, version, key);
    }

    public void insertIdea(UUID id, UUID runId, UUID userId, int score, String breakdown, String title, String why, String body, String[] patterns) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ideas (id, run_id, user_id, score, breakdown, title, why, body, pattern_ids, status)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, 'saved')
                    """);
            ps.setObject(1, id);
            ps.setObject(2, runId);
            ps.setObject(3, userId);
            ps.setInt(4, score);
            ps.setString(5, breakdown);
            ps.setString(6, title);
            ps.setString(7, why);
            ps.setString(8, body);
            ps.setArray(9, connection.createArrayOf("text", patterns));
            return ps;
        });
    }

    public List<Map<String, Object>> listIdeas(UUID userId) {
        return jdbc.query("""
                SELECT id, title, why, score, breakdown::text, body::text, feedback, tried
                FROM ideas WHERE user_id = ? ORDER BY created_at DESC LIMIT 50
                """, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id").toString());
            row.put("title", rs.getString("title"));
            row.put("why", rs.getString("why"));
            row.put("score", rs.getBigDecimal("score"));
            row.put("breakdown", readJson(rs.getString("breakdown")));
            row.put("body", readJson(rs.getString("body")));
            row.put("feedback", rs.getObject("feedback"));
            row.put("tried", rs.getBoolean("tried"));
            return row;
        }, userId);
    }

    public Map<String, Object> findIdea(UUID userId, UUID ideaId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT i.title, i.why, i.body::text AS body, r.title AS news_title, r.url AS news_url, r.industry
                FROM ideas i
                JOIN idea_runs r ON r.id = i.run_id
                WHERE i.id = ? AND i.user_id = ?
                """, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", rs.getString("title"));
            row.put("why", rs.getString("why"));
            row.put("body", readJson(rs.getString("body")));
            row.put("newsTitle", rs.getString("news_title"));
            row.put("newsUrl", rs.getString("news_url"));
            row.put("industry", rs.getString("industry"));
            return row;
        }, ideaId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void feedbackIdea(UUID userId, UUID ideaId, Integer feedback, Boolean tried) {
        jdbc.update(
                "UPDATE ideas SET feedback = COALESCE(?, feedback), tried = COALESCE(?, tried) WHERE id = ? AND user_id = ?",
                feedback, tried, ideaId, userId);
    }

    public int countProjects(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM projects WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public UUID insertProject(UUID userId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO projects (id, user_id, name) VALUES (?, ?, ?)", id, userId, name);
        return id;
    }

    public void insertProjectItem(UUID projectId, String kind, String refJson) {
        jdbc.update(
                "INSERT INTO project_items (id, project_id, kind, ref) VALUES (?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), projectId, kind, refJson);
    }

    public List<Map<String, Object>> listProjects(UUID userId) {
        return jdbc.query(
                "SELECT id, name FROM projects WHERE user_id = ? ORDER BY created_at DESC",
                (rs, i) -> Map.of("id", rs.getObject("id").toString(), "name", rs.getString("name")),
                userId);
    }

    public List<Map<String, Object>> projectItems(UUID projectId, UUID userId) {
        return jdbc.query("""
                SELECT i.kind, i.ref::text FROM project_items i
                JOIN projects p ON p.id = i.project_id
                WHERE i.project_id = ? AND p.user_id = ?
                """, (rs, i) -> Map.of("kind", rs.getString(1), "ref", readJson(rs.getString(2))), projectId, userId);
    }

    public void savePassages(String searchKey, List<Map<String, Object>> passages) {
        for (Map<String, Object> passage : passages) {
            jdbc.update(
                    "INSERT INTO finding_passages (id, search_key, finding_id, url, passage_text, citation_no) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    searchKey,
                    str(passage.get("findingId")),
                    str(passage.get("url")),
                    str(passage.get("text")),
                    intVal(passage.get("citation"), 0));
        }
    }

    public void saveFeedback(UUID userId, String url, int vote) {
        jdbc.update("INSERT INTO finding_feedback (id, user_id, url, vote) VALUES (?, ?, ?, ?)", UUID.randomUUID(), userId, url, vote);
    }

    public List<Map<String, Object>> catalogItems() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(readCatalog("tools"));
        rows.addAll(readCatalog("courses"));
        return rows;
    }

    public void flagStaleCatalog() {
        jdbc.update("UPDATE tools SET stale = true WHERE last_verified_at < CURRENT_DATE - INTERVAL '30 days'");
        jdbc.update("UPDATE courses SET stale = true WHERE last_verified_at < CURRENT_DATE - INTERVAL '30 days'");
    }

    public void saveWeekly(UUID userId, UUID ideaId, int hours, boolean lighter, List<Map<String, Object>> weeks) {
        UUID planId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO weekly_plans (id, user_id, idea_id, hours_per_week, lighter) VALUES (?, ?, ?, ?, ?)",
                planId, userId, ideaId, hours, lighter);
        for (Map<String, Object> week : weeks) {
            jdbc.update(
                    "INSERT INTO weekly_tasks (id, plan_id, week_no, outcome, tasks, metric, done_state) VALUES (?, ?, ?, ?, ?::jsonb, ?, NULL)",
                    UUID.randomUUID(),
                    planId,
                    intVal(week.get("weekNo"), 0),
                    str(week.get("outcome")),
                    writeJson(week.get("tasks")),
                    str(week.get("metric")));
        }
    }

    public void checkin(UUID userId, UUID taskId, String state) {
        jdbc.update("""
                UPDATE weekly_tasks t SET done_state = ?
                FROM weekly_plans p
                WHERE t.id = ? AND t.plan_id = p.id AND p.user_id = ?
                """, state, taskId, userId);
    }

    public void saveOutline(UUID userId, UUID ideaId, String content, String version) {
        jdbc.update(
                "INSERT INTO business_outlines (id, user_id, idea_id, content, version) VALUES (?, ?, ?, ?::jsonb, ?)",
                UUID.randomUUID(), userId, ideaId, content, version);
    }

    public String latestOutline(UUID userId, UUID ideaId) {
        List<String> rows = jdbc.query(
                "SELECT content::text FROM business_outlines WHERE user_id = ? AND idea_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> rs.getString(1), userId, ideaId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Double> packWeights(UUID userId) {
        Map<String, Double> weights = new LinkedHashMap<>();
        jdbc.query("SELECT host, weight FROM source_pack_domains", rs -> {
            weights.put(rs.getString("host"), rs.getDouble("weight"));
        });
        if (userId != null) {
            jdbc.query("""
                    SELECT d.host FROM source_pack_domains d
                    JOIN user_pack_prefs p ON p.pack_id = d.pack_id
                    WHERE p.user_id = ? AND p.enabled = false
                    """, rs -> {
                weights.put(rs.getString("host"), 0.6);
            }, userId);
        }
        return weights;
    }

    public void setPackEnabled(UUID userId, String packId, boolean enabled) {
        jdbc.update("""
                INSERT INTO user_pack_prefs (user_id, pack_id, enabled) VALUES (?, ?, ?)
                ON CONFLICT (user_id, pack_id) DO UPDATE SET enabled = EXCLUDED.enabled
                """, userId, packId, enabled);
    }

    public void saveQuery(UUID userId, String query, String industry) {
        jdbc.update("INSERT INTO saved_queries (id, user_id, query, industry) VALUES (?, ?, ?, ?)", UUID.randomUUID(), userId, query, industry);
    }

    public List<Map<String, Object>> savedQueries() {
        return jdbc.query("SELECT user_id, query, industry FROM saved_queries", (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", rs.getObject("user_id", UUID.class));
            item.put("query", rs.getString("query"));
            item.put("industry", rs.getString("industry"));
            return item;
        });
    }

    public java.util.Set<String> digestHashes(UUID userId) {
        java.util.Set<String> hashes = new java.util.HashSet<>();
        jdbc.query("SELECT url_hash FROM digests WHERE user_id = ?", rs -> {
            hashes.add(rs.getString("url_hash"));
        }, userId);
        return hashes;
    }

    public boolean markDigest(UUID userId, String urlHash) {
        int updated = jdbc.update(
                "INSERT INTO digests (id, user_id, url_hash, sent_at) VALUES (?, ?, ?, now()) ON CONFLICT (user_id, url_hash) DO NOTHING",
                UUID.randomUUID(), userId, urlHash);
        return updated > 0;
    }

    public boolean tryLock(String name, String owner) {
        jdbc.update("INSERT INTO job_locks (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
        int updated = jdbc.update("""
                UPDATE job_locks SET locked_until = now() + INTERVAL '30 minutes', owner = ?
                WHERE name = ? AND (locked_until IS NULL OR locked_until < now())
                """, owner, name);
        return updated == 1;
    }

    public int referralCount(UUID referrerId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM referral_redemptions WHERE referrer_id = ?",
                Integer.class, referrerId);
        return count == null ? 0 : count;
    }

    public boolean alreadyRedeemed(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM referral_redemptions WHERE redeemed_by = ?",
                Integer.class, userId);
        return count != null && count > 0;
    }

    public UUID referrerForCode(String code) {
        List<UUID> rows = jdbc.query("SELECT id FROM users", (rs, i) -> rs.getObject("id", UUID.class));
        for (UUID id : rows) {
            if (com.gyanwire.referrals.ReferralCodes.codeFor(id).equals(code)) {
                return id;
            }
        }
        return null;
    }

    public void redeem(UUID referrerId, UUID userId, String code, int bonus) {
        jdbc.update(
                "INSERT INTO referral_redemptions (id, code, referrer_id, redeemed_by) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), code, referrerId, userId);
        jdbc.update("""
                INSERT INTO referral_bonus (user_id, extra_searches) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET extra_searches = referral_bonus.extra_searches + EXCLUDED.extra_searches
                """, referrerId, bonus);
        jdbc.update("""
                INSERT INTO referral_bonus (user_id, extra_searches) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET extra_searches = referral_bonus.extra_searches + EXCLUDED.extra_searches
                """, userId, bonus);
    }

    public int bonusSearches(UUID userId) {
        if (userId == null) {
            return 0;
        }
        try {
            Integer extra = jdbc.queryForObject(
                    "SELECT extra_searches FROM referral_bonus WHERE user_id = ?",
                    Integer.class, userId);
            return extra == null ? 0 : extra;
        } catch (Exception e) {
            return 0;
        }
    }

    public void setDigestOptIn(UUID userId, boolean optedIn) {
        jdbc.update("UPDATE user_profiles SET digest_opt_in = ? WHERE user_id = ?", optedIn, userId);
    }

    private List<Map<String, Object>> readCatalog(String table) {
        return jdbc.query("SELECT id, name, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, bucket FROM " + table, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("name", rs.getString("name"));
            row.put("costInr", rs.getInt("cost_inr"));
            row.put("billing", rs.getString("billing"));
            row.put("freeAlternativeId", rs.getString("free_alternative_id"));
            row.put("skills", array(rs.getArray("unlocks_skills")));
            row.put("weeksSaved", rs.getInt("weeks_saved"));
            row.put("priority", rs.getInt("priority"));
            row.put("bucket", rs.getString("bucket"));
            row.put("free", rs.getInt("cost_inr") == 0);
            return row;
        });
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean columnExists(java.sql.ResultSet rs, String name) {
        try {
            rs.findColumn(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> array(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] values) {
            return List.of(values);
        }
        return List.of();
    }

    private static Array textArray(java.sql.Connection connection, Object value) throws java.sql.SQLException {
        List<String> items = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    items.add(String.valueOf(item));
                }
            }
        }
        return connection.createArrayOf("text", items.toArray(String[]::new));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
