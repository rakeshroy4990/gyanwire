-- Catalog explainers: optional link + short blurb for plan gear (SWAYAM first).

ALTER TABLE tools ADD COLUMN IF NOT EXISTS url TEXT;
ALTER TABLE tools ADD COLUMN IF NOT EXISTS blurb TEXT;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS url TEXT;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS blurb TEXT;

UPDATE courses
SET url = 'https://swayam.gov.in',
    blurb = 'India’s free government learning platform (Study Webs of Active-learning for Young Aspiring Minds). University and school courses online — not a SEBI or share-market class.'
WHERE id = 'swayam';

UPDATE tools
SET url = 'https://code.visualstudio.com',
    blurb = 'Free code editor from Microsoft. Use it to write and save the artifacts in your build weeks.'
WHERE id = 'vscode';
