package com.gyanwire.actionplan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * After ≥5 measured timings for a tool/step, upsert tool_step_effects with median (basis=measured).
 */
@Component
public class StepTimingCalibrationJob {

    private final JdbcTemplate jdbc;

    public StepTimingCalibrationJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void calibrate() {
        try {
            List<Map<String, Object>> groups = jdbc.queryForList("""
                    SELECT tool_id, step_key, COUNT(*) AS n,
                           PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY minutes_actual) AS median_minutes
                    FROM step_timings
                    WHERE tool_id IS NOT NULL AND tool_id <> ''
                    GROUP BY tool_id, step_key
                    HAVING COUNT(*) >= 5
                    """);
            for (Map<String, Object> row : groups) {
                String toolId = String.valueOf(row.get("tool_id"));
                String stepKey = String.valueOf(row.get("step_key"));
                int n = ((Number) row.get("n")).intValue();
                int median = (int) Math.round(((Number) row.get("median_minutes")).doubleValue());
                jdbc.update("""
                        INSERT INTO tool_step_effects (tool_id, step_key, format, minutes, basis, n, confidence, notes)
                        VALUES (?, ?, 'short_vertical', ?, 'measured', ?, 0.7, 'median of user timings')
                        ON CONFLICT (tool_id, step_key, format) DO UPDATE SET
                            minutes = EXCLUDED.minutes,
                            basis = 'measured',
                            n = EXCLUDED.n,
                            confidence = EXCLUDED.confidence,
                            notes = EXCLUDED.notes
                        """, toolId, stepKey, median, n);
            }
        } catch (Exception ignored) {
            // Table may be missing in unit tests.
        }
    }
}
