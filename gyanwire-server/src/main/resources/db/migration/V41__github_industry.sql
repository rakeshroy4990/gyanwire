-- GitHub research section: famous open-source projects as idea seeds.

INSERT INTO research_industries (name, label, sort_order)
VALUES ('GitHub', 'GitHub projects', 12)
ON CONFLICT (name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

INSERT INTO research_industry_subs (industry_id, name, label, sort_order)
SELECT i.id, s.name, s.label, s.sort_order
FROM research_industries i
JOIN (
    VALUES
        ('GitHub', 'Stars', 'Most starred projects', 10),
        ('GitHub', 'Trending', 'Recently active projects', 20),
        ('GitHub', 'AI', 'AI and ML projects', 30),
        ('GitHub', 'DevTools', 'Developer tools', 40),
        ('GitHub', 'Web', 'Web projects', 50),
        ('GitHub', 'Data', 'Data and databases', 60),
        ('GitHub', 'Mobile', 'Mobile projects', 70)
) AS s(industry_name, name, label, sort_order)
  ON i.name = s.industry_name
ON CONFLICT (industry_id, name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
VALUES
    ('GitHub', NULL, 'github_search', 'GitHub Search',
     'https://api.github.com/search/repositories?q={query}&sort=stars&order=desc&per_page=30',
     'GLOBAL', 1.50, TRUE, NULL);

-- Query is the GitHub search q= parameter. Trending gets a pushed:> date at ingest time.
INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    ('GitHub', NULL, 'stars', 'stars:>20000', 30, 'GLOBAL'),
    ('GitHub', 'Stars', 'stars', 'stars:>50000', 30, 'GLOBAL'),
    ('GitHub', 'Trending', 'stars', 'stars:>1000', 30, 'GLOBAL'),
    ('GitHub', 'AI', 'stars', 'topic:machine-learning stars:>2000', 30, 'GLOBAL'),
    ('GitHub', 'AI', 'stars', 'topic:llm OR topic:generative-ai stars:>1000', 30, 'GLOBAL'),
    ('GitHub', 'DevTools', 'stars', 'topic:developer-tools stars:>2000', 30, 'GLOBAL'),
    ('GitHub', 'DevTools', 'stars', 'topic:cli OR topic:devtools stars:>2000', 30, 'GLOBAL'),
    ('GitHub', 'Web', 'stars', 'topic:javascript OR topic:typescript stars:>10000', 30, 'GLOBAL'),
    ('GitHub', 'Web', 'stars', 'topic:web stars:>5000', 30, 'GLOBAL'),
    ('GitHub', 'Data', 'stars', 'topic:database OR topic:data-science stars:>3000', 30, 'GLOBAL'),
    ('GitHub', 'Mobile', 'stars', 'topic:android OR topic:ios OR topic:flutter stars:>3000', 30, 'GLOBAL');
