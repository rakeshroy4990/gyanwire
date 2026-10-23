---
name: break-audit
description: >-
  Live break-test of a website with URL, username, and password: security
  vulnerabilities, API abuse, functional breaks, and UX issues. Use when the
  user asks to break, pen-test, security-test, QA-abuse, or audit a site with
  credentials, or says "try to break the website".
---

# Break-audit (live site)

Authorized break-test only. Use credentials the user supplied for a site they own or control. Never store passwords in the skill, git, or run records committed to the repo.

## Inputs (required)

| Input | Example |
|---|---|
| Site URL | `http://localhost:5180/` |
| Username / email | `user@example.com` |
| Password | (session only — do not write into files that get committed) |

Optional: `--api` base (Gyanwire default `http://localhost:8080`). If omitted, derive from [scripts/discover_api.py](scripts/discover_api.py).

Optional destructive flag: only when the user explicitly says to run destructive probes (profile wipe, mass delete). Default is **safe mode** (no irreversible data loss).

## Hard rules

1. Read this skill and follow every phase before reporting.
2. Run the API probe script. Do not invent API findings from memory.
3. Drive the browser (cursor-ide-browser) for login, navigation, XSS reflection, empty states, and UX.
4. Write `break-audit-run.json` (see below) and validate:
   ```bash
   python3 .cursor/skills/break-audit/scripts/validate_run.py break-audit-run.json
   ```
5. Show findings only after that command prints `OK`.
6. Do not commit passwords, cookie jars, or raw JWT strings.
7. Safe mode by default: skip `DELETE` profile, account wipe, payment capture. Note skipped destructive checks in the report.
8. Prefer evidence: status codes, response snippets (redact secrets), screenshot observations, element refs.

## Progress checklist

Copy and tick while working:

```
Break-audit progress:
- [ ] Phase 0 — Scope + discover API
- [ ] Phase 1 — API probe script
- [ ] Phase 2 — Browser auth + session
- [ ] Phase 3 — Injection / XSS / open redirects
- [ ] Phase 4 — AuthZ / IDOR / admin
- [ ] Phase 5 — Abuse / rate limits / races
- [ ] Phase 6 — Functional breaks
- [ ] Phase 7 — UX / a11y / empty / mobile
- [ ] Phase 8 — Run record + validate_run.py OK
- [ ] Phase 9 — Report
```

## Phase 0 — Scope + discover API

```bash
python3 .cursor/skills/break-audit/scripts/discover_api.py --site "$SITE"
```

Record `site`, `api`, and whether health responded.

## Phase 1 — API probe script

```bash
python3 .cursor/skills/break-audit/scripts/probe_api.py \
  --site "$SITE" \
  --api "$API" \
  --email "$EMAIL" \
  --password "$PASSWORD" \
  --out probe-findings.json
```

Add `--destructive` only if the user opted in.

Merge every `findings[]` item into the final report. Do not drop Critical/High.

Full probe catalog (what the script covers and what you must still do in the browser): [CHECKLIST.md](CHECKLIST.md).

## Phase 2 — Browser auth + session

1. Open `$SITE`. Snapshot.
2. Sign in with supplied credentials (dispatch `input` events if Vue controlled inputs ignore fill).
3. Confirm signed-in chrome (name, Account).
4. Refresh; confirm session sticks.
5. Sign out / protected-route spot check if Account exists.
6. Note login friction (disabled submit, error copy, password show).

## Phase 3 — Injection / XSS / open redirects

In the browser (and via crafted URLs):

- Reflect XSS in query params used as titles/links (`?title=`, `?url=`, `?q=`).
- Confirm any `:href` bound to user/query data rejects `javascript:` and non-http(s).
- Try obvious XSS in research / chat / profile text fields; note whether it executes or only reflects as text.
- Do not claim stored XSS without a second load proving persistence.

## Phase 4 — AuthZ / IDOR / admin

Covered largely by `probe_api.py`. Also manually:

- Open Account / Profile / Billing while logged in.
- Hit admin URLs if linked in UI.
- Try another user's UUID in plan/idea URLs if any appear in the UI.

## Phase 5 — Abuse / rate limits / races

- Parallel expensive actions (ideas, search) if the product has quotas.
- Feedback / dismiss / vote without login.
- Referral code predictability.
- Quota UI vs `/api/me/usage` drift.

## Phase 6 — Functional breaks

- Industry filters, deep links (`?industry=`), pagination, empty industries.
- Core CTA after quota exhausted (dead end vs upgrade path).
- Case-sensitive API paths, 500s on bad input.
- First-load empty flash while data is loading.

## Phase 7 — UX / a11y / mobile

- First viewport: loading skeletons vs "Nothing here yet" lie.
- Contrast on secondary chips / feedback links.
- Unlabeled controls (e.g. `f1`–`f5`).
- Emulate ~390px width; note overflow, unreachable CTAs, stacked filters.
- Limit-reached and error pages: message + next action.

## Phase 8 — Run record + validate

Write `break-audit-run.json` (no password field):

```json
{
  "site": "http://localhost:5180/",
  "api": "http://localhost:8080",
  "email": "user@example.com",
  "safeMode": true,
  "phases": {
    "discover": true,
    "apiProbe": true,
    "browserAuth": true,
    "injection": true,
    "authz": true,
    "abuse": true,
    "functional": true,
    "ux": true
  },
  "probeFindingsPath": "probe-findings.json",
  "findingCount": 12,
  "criticalCount": 2
}
```

```bash
python3 .cursor/skills/break-audit/scripts/validate_run.py break-audit-run.json
```

## Phase 9 — Report format

Lead with a 1–2 sentence verdict. Then:

### Critical
Numbered list: issue · evidence · fix

### High
Same

### Medium / UX
Same

### Held up
What resisted abuse (keep short)

### Suggested fix order
Ordered list of the top fixes

Do not dump raw JWTs or passwords. Redact cookie values.

## Gyanwire-specific notes

- UI port often `5180`; API `8080` (`VITE_BACKEND_URL` / `GYANWIRE_SERVER_PORT`).
- Auth cookies: httpOnly `access_token`, `refresh_token`.
- High-value surfaces: news list, Get an idea, research chat, profile, billing, referrals.
- Known historical failure classes (always re-test, never assume fixed): see [CHECKLIST.md](CHECKLIST.md) § Historical Gyanwire findings.
