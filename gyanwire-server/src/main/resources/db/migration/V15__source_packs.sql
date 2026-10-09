
CREATE TABLE IF NOT EXISTS source_packs (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    industry TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS source_pack_domains (
    pack_id TEXT NOT NULL REFERENCES source_packs (id),
    host TEXT NOT NULL,
    weight NUMERIC NOT NULL DEFAULT 1,
    PRIMARY KEY (pack_id, host)
);
CREATE TABLE IF NOT EXISTS user_pack_prefs (
    user_id UUID NOT NULL REFERENCES users (id),
    pack_id TEXT NOT NULL REFERENCES source_packs (id),
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, pack_id)
);
INSERT INTO source_packs (id, name, industry) VALUES ('share-market', 'share-market', 'share-market') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('it', 'it', 'it') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('medical', 'medical', 'medical') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('space', 'space', 'space') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('gaming', 'gaming', 'gaming') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('social-media', 'social-media', 'social-media') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('astrology', 'astrology', 'astrology') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_packs (id, name, industry) VALUES ('cross', 'cross', 'cross') ON CONFLICT (id) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('share-market', 'sebi.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('share-market', 'rbi.org.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('share-market', 'nseindia.com', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('share-market', 'bseindia.com', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('share-market', 'mospi.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('it', 'arxiv.org', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('it', 'github.com', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('it', 'huggingface.co', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('it', 'meity.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('it', 'nasscom.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('medical', 'pubmed.ncbi.nlm.nih.gov', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('medical', 'icmr.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('medical', 'cdsco.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('medical', 'who.int', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('medical', 'cochrane.org', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('space', 'isro.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('space', 'inspace.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('space', 'nasa.gov', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('space', 'esa.int', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('gaming', 'meity.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('gaming', 'arxiv.org', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('social-media', 'meity.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('astrology', 'en.wikipedia.org', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('cross', 'pib.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('cross', 'startupindia.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('cross', 'dpiit.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
INSERT INTO source_pack_domains (pack_id, host, weight) VALUES ('cross', 'niti.gov.in', 1.4) ON CONFLICT (pack_id, host) DO NOTHING;
SELECT gyanwire_lock('source_packs');
SELECT gyanwire_lock('source_pack_domains');
SELECT gyanwire_lock('user_pack_prefs');
