# Release notes

Eval baseline and the latest offline run are the same file: `eval/reports/baseline-2026-10-09.json`.

| Metric | Baseline | Latest |
|---|---|---|
| nDCG@5 | 1.000 | 1.000 |
| precision@5 | 1.000 | 1.000 |
| latency | 80 ms | 80 ms |
| hybrid nDCG@5 | 1.000 | 1.000 |

The golden set is 10 labelled queries for each of the seven industries (70 total). CI fails when nDCG@5 drops more than 3 points versus that baseline. Hybrid full-text fusion did not lower nDCG@5.

The Playwright script is `e2e/journey.spec.js` (`npm run test:e2e`) for the mobile 390px and desktop journey across home, pricing, and the legal pages. It needs the UI on `http://localhost:5180`.

Ops still to do outside this build: a tested Postgres restore, secrets outside `.env` in production, and JWT secret rotation with a key id. Legal copy is marked REVIEW WITH LAWYER.
