-- GitHub browse: no day/pushed search window. Floor every pack at stars:>1000
-- while keeping topic chips distinct.

UPDATE news_query_packs
SET query = 'stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub'
  AND (sub IS NULL OR sub IN ('Stars', 'Trending'));

UPDATE news_query_packs
SET query = 'topic:machine-learning OR topic:llm OR topic:generative-ai stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub' AND sub = 'AI';

UPDATE news_query_packs
SET query = 'topic:developer-tools OR topic:cli OR topic:devtools stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub' AND sub = 'DevTools';

UPDATE news_query_packs
SET query = 'topic:javascript OR topic:typescript OR topic:web stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub' AND sub = 'Web';

UPDATE news_query_packs
SET query = 'topic:database OR topic:data-science stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub' AND sub = 'Data';

UPDATE news_query_packs
SET query = 'topic:android OR topic:ios OR topic:flutter stars:>1000',
    signal_type = 'stars'
WHERE industry = 'GitHub' AND sub = 'Mobile';

-- Collapse duplicate topic packs left from V41 (two rows per sub) to one query each.
DELETE FROM news_query_packs a
    USING news_query_packs b
WHERE a.industry = 'GitHub'
  AND b.industry = 'GitHub'
  AND a.sub IS NOT DISTINCT FROM b.sub
  AND a.id > b.id;
