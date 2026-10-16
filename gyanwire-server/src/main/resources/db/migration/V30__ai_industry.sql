-- AI is its own research section, ahead of Share Market, with lab subsections.
-- Query packs stay on feature launches from the current model wave.

INSERT INTO research_industries (name, label, sort_order)
VALUES ('AI', 'AI features', 5)
ON CONFLICT (name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

UPDATE research_industry_subs
SET deleted = TRUE,
    updated_at = NOW()
WHERE name = 'AI'
  AND industry_id = (SELECT id FROM research_industries WHERE name = 'IT');

INSERT INTO research_industry_subs (industry_id, name, label, sort_order)
SELECT i.id, s.name, s.label, s.sort_order
FROM research_industries i
JOIN (
    VALUES
        ('AI', 'Anthropic', 'Anthropic features', 10),
        ('AI', 'OpenAI', 'OpenAI features', 20),
        ('AI', 'Grok', 'Grok features', 30),
        ('AI', 'Google', 'Google features', 40),
        ('AI', 'Meta', 'Meta features', 50),
        ('AI', 'Microsoft', 'Microsoft features', 60)
) AS s(industry_name, name, label, sort_order)
  ON i.name = s.industry_name
ON CONFLICT (industry_id, name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

UPDATE news_query_packs
SET enabled = FALSE
WHERE industry = 'IT'
  AND (
    sub = 'AI'
    OR query LIKE 'AI (launches%'
  );

INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
VALUES
    ('AI', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.20, TRUE, NULL),
    ('AI', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL);

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    ('AI', NULL, 'launch', '("GPT-6" OR "Intelligent UI" OR "Claude Haiku 5.5" OR "Grok 4.7" OR "Gemini 3.8") (launches OR unveils OR "rolls out" OR introduces) when:7d', 7, 'IN'),
    ('AI', NULL, 'launch', '(OpenAI OR Anthropic OR xAI OR Gemini OR "Meta AI" OR Copilot) (launches OR unveils OR "rolls out") (feature OR model) when:7d', 7, 'IN'),
    ('AI', NULL, 'launch', '(ChatGPT OR Claude OR Grok) (feature OR "rolls out" OR launches OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Anthropic', 'launch', 'Anthropic Claude ("Haiku 5.5" OR "Sonnet 5.5" OR "Opus 5.5" OR "Fable 5.1") (launches OR unveils OR "rolls out" OR available) when:7d', 7, 'IN'),
    ('AI', 'Anthropic', 'launch', 'Claude (feature OR "1M context") (launches OR introduces OR unveils) when:7d', 7, 'IN'),
    ('AI', 'OpenAI', 'launch', 'OpenAI ("GPT-6" OR "GPT-6 Sol" OR "GPT-6 Luna" OR "Intelligent UI") (launches OR unveils OR "rolls out") when:7d', 7, 'IN'),
    ('AI', 'OpenAI', 'launch', 'ChatGPT ("Intelligent UI" OR "GPT-6") (feature OR launches OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Grok', 'launch', '("Grok 4.7" OR xAI) (launches OR unveils OR "rolls out" OR available) (model OR API OR feature) when:7d', 7, 'IN'),
    ('AI', 'Grok', 'launch', 'Grok (coding OR "Grok Build" OR API) (feature OR launches OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Google', 'launch', '(Gemini OR "EmbeddingGemma 2" OR "Gemini agent") (launches OR unveils OR "rolls out") when:7d', 7, 'IN'),
    ('AI', 'Google', 'launch', '"Gemini 3.8" (Flash OR Live OR feature) (launches OR available OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Meta', 'launch', '("Muse Image" OR "Meta AI" OR "Llama 4") (launches OR unveils OR "rolls out") (feature OR model OR API) when:7d', 7, 'IN'),
    ('AI', 'Meta', 'launch', 'Meta (AI) (image OR Llama) (feature OR launches OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Microsoft', 'launch', '("Microsoft 365 Copilot" OR "Copilot Chat") (skills OR launches OR "rolls out" OR unveils) when:7d', 7, 'IN'),
    ('AI', 'Microsoft', 'launch', '("Windows ML" OR Copilot) ("llama.cpp" OR Autopilot OR "Hybrid Intelligence" OR feature) (launches OR "rolls out") when:7d', 7, 'IN'),

    ('AI', NULL, 'launch', '("GPT-6" OR "Intelligent UI" OR "Claude Haiku 5.5" OR "Grok 4.7" OR "Gemini 3.8") (launches OR unveils OR "rolls out" OR introduces) when:7d', 7, 'GLOBAL'),
    ('AI', NULL, 'launch', '(OpenAI OR Anthropic OR xAI OR Gemini OR "Meta AI" OR Copilot) (launches OR unveils OR "rolls out") (feature OR model) when:7d', 7, 'GLOBAL'),
    ('AI', NULL, 'launch', '(ChatGPT OR Claude OR Grok) (feature OR "rolls out" OR launches OR unveils) when:7d', 7, 'GLOBAL'),
    ('AI', 'Anthropic', 'launch', 'Anthropic Claude ("Haiku 5.5" OR "Sonnet 5.5" OR "Opus 5.5" OR "Fable 5.1") (launches OR unveils OR "rolls out" OR available) when:7d', 7, 'GLOBAL'),
    ('AI', 'OpenAI', 'launch', 'OpenAI ("GPT-6" OR "Intelligent UI") (launches OR unveils OR "rolls out") when:7d', 7, 'GLOBAL'),
    ('AI', 'Grok', 'launch', '("Grok 4.7" OR xAI) (launches OR unveils OR "rolls out") (feature OR model) when:7d', 7, 'GLOBAL'),
    ('AI', 'Google', 'launch', '(Gemini OR "EmbeddingGemma 2" OR "Gemini agent") (launches OR unveils OR "rolls out") when:7d', 7, 'GLOBAL'),
    ('AI', 'Meta', 'launch', '("Muse Image" OR "Llama 4" OR "Meta AI") (launches OR unveils OR "rolls out") when:7d', 7, 'GLOBAL'),
    ('AI', 'Microsoft', 'launch', '(Copilot OR "Windows ML") (skills OR "llama.cpp" OR Autopilot OR launches OR "rolls out") when:7d', 7, 'GLOBAL');
