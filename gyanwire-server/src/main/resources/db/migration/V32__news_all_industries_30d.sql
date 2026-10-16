-- Widen every industry query pack to a 30-day Google window (daily-first ranking stays in app code).

UPDATE news_query_packs
SET query = replace(query, 'when:7d', 'when:30d'),
    window_days = 30
WHERE enabled = TRUE
  AND query LIKE '%when:7d%';
