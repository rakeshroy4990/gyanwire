# Gyanwire — Cursor Plan: from tool to paid product

How to use this file:
1. Put it in the repo root next to `ARCHITECTURE.md`.
2. Work **one step at a time**. Each step = one git branch, one Cursor chat (Agent mode), one PR.
3. In each Cursor chat, start with: `@ARCHITECTURE.md @CURSOR_PLAN.md — implement Step N only. Don't touch anything outside its scope.`
4. Paste the step's **Prompt** block. Review the diff, run the **Done when** checks, commit, then move on.

Tip: use Plan mode first for Steps 1, 3, 4 and 8 (they touch many files), then switch to Agent mode.

---

## Step 0 — Cursor project setup (30 min)

**Goal:** Cursor knows your conventions so every later step stays consistent.

Create `.cursor/rules/gyanwire.mdc`:

```
---
alwaysApply: true
---
- Stack: Vue 3 + Pinia + Vite (client/), Express + pg (server/). JavaScript, ESM.
- Components never call APIs directly; go through composables -> services.
- JWTs only in httpOnly cookies. Never use localStorage for tokens.
- All secrets from root .env. Never hardcode keys. Update .env.example for every new variable.
- Every new route gets input validation (zod) and a basic test.
- Every new DB change is a numbered SQL migration in server/db/migrations/.
- Keep changes scoped to the current step in CURSOR_PLAN.md.
```

Then:
- Create branch `main` -> `step-0-setup`.
- Add `vitest` (server) and a `npm test` script if missing.
- Add `.env.example` listing every current env var.

**Done when:** `npm test` runs (even with one smoke test), `.env.example` exists.

---

## Step 1 — Separate Gyanwire's database from the hospital's

**Why:** A research product must never share a user table with patient-platform data. Also required for any future sale.

**Prompt:**
```
Decouple Gyanwire auth from the hospital Supabase DB.
- Rename env vars from SPRING_DATASOURCE_* to GYANWIRE_DATABASE_URL (single connection string). Keep backward-compat only for local dev if trivial.
- Add a migration system: server/db/migrations/001_init.sql creating users, refresh_tokens (own schema, UUID ids, email unique, created_at, deleted_at, role default 'user').
- Update userRepository.js and refreshTokenRepository.js to the new schema. No hospital-specific columns (external_id, patient roles).
- Write a one-off script server/scripts/export-gyanwire-users.js that copies only users who signed in via Gyanwire, if any exist.
- Update ARCHITECTURE.md sections 3.2, 4.5, 5 accordingly.
```

**Manual:** create a new free Supabase/Neon project for Gyanwire and put its URL in `.env`.

**Done when:** register/login/Google/logout/me all work against the new DB; no reference to hospital tables remains (`grep -ri "spring_datasource\|hospital" server/`).

---

## Step 2 — Plans, usage metering and limits

**Goal:** know who is Free/Pro/Team and stop free users exceeding limits.

**Prompt:**
```
Add plans and usage metering.
Migration 002: tables `plans` (id, name, daily_search_limit, can_save, can_export, can_alert, seats) seeded with free/pro/team; `subscriptions` (user_id, plan_id, status, provider, provider_subscription_id, current_period_end); `usage_events` (id, user_id, kind, cost_inr_estimate, created_at).
Server: middleware `requirePlanLimit('search')` applied to POST /api/search that
 - resolves user's plan (default free; anonymous = 2 searches/day by IP via a table or in-memory LRU),
 - counts today's usage_events,
 - returns 402 with {code:'LIMIT_REACHED', upgradeUrl:'/pricing'} when exceeded,
 - logs a usage_event after a successful search.
Add GET /api/me/usage returning plan and remaining searches.
Client: show "3 of 5 searches left today" in ResearchWorkspace and an upgrade prompt on 402.
```

**Done when:** a free user gets blocked on the 6th search; usage endpoint returns correct counts; tests cover the middleware.

---

## Step 3 — Razorpay subscriptions

**Prompt:**
```
Integrate Razorpay Subscriptions (test mode first).
Server:
- POST /api/billing/checkout {planId, interval} -> creates Razorpay subscription, returns subscription id + key id.
- POST /api/billing/webhook -> verify X-Razorpay-Signature using raw body; handle subscription.activated, charged, halted, cancelled, completed; update `subscriptions` table idempotently (store event ids).
- POST /api/billing/cancel.
- GET /api/billing/status.
Client:
- /pricing page with Free / Pro / Team cards, monthly/annual toggle.
- Razorpay Checkout.js flow in a billing.service.js + useBilling composable; success page refreshes plan from /api/me/usage.
- "Manage subscription" in the header menu.
Env: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET, plan ids per plan/interval. Add to .env.example.
```

**Manual:** create Razorpay account, create Plans (Pro monthly/annual, Team monthly), set webhook URL (use a tunnel like ngrok/cloudflared locally).

**Done when:** a test-mode payment upgrades the user to Pro; cancelling downgrades at period end; replaying a webhook changes nothing.

---

## Step 4 — Search provider abstraction, caching, rate limiting

**Why:** Scraping DuckDuckGo at scale will get you blocked, and caching cuts cost.

**Prompt:**
```
Refactor engine/discover.js behind a provider interface.
- Create engine/providers/{ddg.js, brave.js, serper.js, firecrawl.js, index.js}. Each exports `discover({query, industry, limit}) -> [{url,title,snippet,source}]`.
- SEARCH_PROVIDER env selects the primary; fall back to ddg on failure.
- Add a query cache table (migration 003: search_cache(query_hash, industry, results_json, created_at)) with 24h TTL for repeated queries.
- Store only title, URL, snippet and extracted key sentences. Do not persist full scraped page text.
- Add express-rate-limit: 60 req/min per IP on /api/*, 10/min on /api/search.
- Add per-search cost estimate to usage_events (provider cost + LLM tokens if used).
- Add GET /api/health fields: provider, db, cache hit rate.
```

**Manual:** get an API key for one provider (Brave Search API, Serper, or Firecrawl) and compare result quality on 10 of your own test questions.

**Done when:** the same query twice is served from cache; killing the primary provider falls back cleanly.

---

## Step 5 — Curated source packs (your moat)

**Goal:** weighted, India-specific source lists per industry, editable without code changes.

**Prompt:**
```
Add source packs.
- Create server/sources/packs/*.json, one per industry (medical, fintech, space, it, sharemarket). Schema: {id, industry, label, domains:[{host, type:'regulator'|'paper'|'news'|'vendor'|'registry'|'patent'|'academic', weight:0-1}], boostTerms:[...], blockHosts:[...]}.
- Seed medical with: cdsco.gov.in, icmr.gov.in, ctri.nic.in, ipindia.gov.in, pubmed.ncbi.nlm.nih.gov, nmc.org.in. Seed fintech with: rbi.org.in, npci.org.in, sebi.gov.in, meity.gov.in. Seed space with isro.gov.in, iist.ac.in. Add a TODO list of more I can fill in.
- engine/pointers.js: load the pack for the selected industry; add domain weight and boostTerms to the score; expose `sourceType` and `scoreBreakdown` on each result.
- engine/discover.js: also run site-restricted queries against the pack's top domains (e.g. `query site:cdsco.gov.in`) in addition to the open query.
- Add a unit test with fixtures proving a regulator page outranks a generic blog for the same text.
```

**Manual (the valuable part):** you curate each pack. Spend real time on medical and fintech; this is the IP.

**Done when:** for 10 benchmark questions per industry, the top 5 contain at least 2 primary sources that Google's first page lacks. Keep this benchmark in `server/test/benchmarks.md`.

---

## Step 6 — Source-type labels and confidence reason in UI

**Prompt:**
```
Update the Findings panel.
- Show a colored badge per sourceType (Regulator, Paper, Registry, Patent, News, Vendor).
- Show the why-line plus a small expandable "score breakdown" (domain weight, term matches, freshness).
- Add filter chips above results to filter by sourceType.
- Add a "Primary sources only" toggle.
Keep components under client/src/components/research/. No API changes beyond fields already added in Step 5.
```

**Done when:** badges and filters work on mobile widths too.

---

## Step 7 — Saved projects and watchlists

**Prompt:**
```
Add saved research projects (Pro/Team feature).
Migration 004: projects(id,user_id,title,industry,subtopic,created_at), project_items(id,project_id,url,title,source_type,note,saved_at), watches(id,project_id,query,industry,frequency,last_run_at,active).
API: CRUD under /api/projects, POST /api/projects/:id/items, DELETE items, POST /api/projects/:id/watch.
All routes require auth and a plan with can_save; free users get a 402 with upgrade hint.
Client: "Save to project" on each finding, a Projects sidebar/page, notes per item, "Watch this query" button.
Add useProjects composable and projects.store.js following existing patterns.
```

**Done when:** a Pro user can save findings, add notes, reload, and see them again; a free user sees the upgrade prompt.

---

## Step 8 — Alerts and weekly digest email

**Prompt:**
```
Add watch execution and email digests.
- server/jobs/runWatches.js: for each active watch due by frequency, run the search pipeline (reusing cache), diff against previously seen URLs (table seen_urls(watch_id,url_hash)), store new findings.
- server/jobs/sendDigests.js: group new findings per user and send one email via Resend (RESEND_API_KEY, MAIL_FROM). HTML + plain text templates in server/emails/.
- Scheduling: use node-cron inside the server when ENABLE_JOBS=true; also expose scripts runnable by an external cron.
- Unsubscribe link with signed token; GET /api/email/unsubscribe.
- Respect plan: Pro max 5 watches, Team max 20.
```

**Manual:** verify a sending domain with Resend (SPF/DKIM) before sending to real users.

**Done when:** creating a watch and running the job manually produces one digest email containing only new URLs.

---

## Step 9 — Export briefs (Markdown, DOCX, PDF)

**Prompt:**
```
Add export of a project or a search result set.
- POST /api/export {projectId | results[], format:'md'|'docx'|'pdf', title}
- Build with `docx` for Word and `pdfkit` or puppeteer-free HTML-to-PDF for PDF. Layout: title, date, query, numbered findings (title, link, source type, why-line, user note), and a "Generated by Gyanwire" footer.
- Gate docx/pdf behind can_export; md allowed for free.
- Client: Export dropdown in the findings panel and project page.
```

**Done when:** a downloaded DOCX and PDF open cleanly with working links.

---

## Step 10 — Optional LLM summaries with hard cost caps

**Prompt:**
```
Add an optional "Summarize findings" feature.
- engine/summary.js: given top N findings (snippets only), produce a 150-word brief with citation numbers. Provider via LLM_PROVIDER env.
- Enforce per-plan monthly token budgets (add columns to plans), log tokens and INR cost estimate in usage_events, and refuse politely when the budget is exhausted.
- Use cache keyed on the result set hash.
- UI: "Summarize" button, loading state, and a notice that summaries can contain errors and must be checked against sources.
```

**Done when:** a Pro user hits their cap and sees a clear message; costs appear in usage_events.

---

## Step 11 — Landing page, legal pages, analytics

**Prompt:**
```
Add public pages without hurting the live-tool feel.
- `/` stays the workspace, but add a collapsible hero strip for signed-out visitors: promise line, 3 example searches that run on click, and a Sign up button.
- Add /pricing (from Step 3), /privacy, /terms, /refund, /contact as simple Vue routes (add vue-router). Draft the legal text as placeholders marked REVIEW WITH LAWYER.
- Add /account page: plan, usage, invoices link, delete my account (soft delete + token revoke + data purge job).
- Add Plausible or PostHog snippet behind VITE_ANALYTICS_ID, tracking: search_run, signup, upgrade_click, subscription_active, export, save.
- Add SEO basics: meta tags, OG image, sitemap.xml, robots.txt.
```

**Done when:** Lighthouse SEO > 90 on `/` and `/pricing`; account deletion works end to end.

---

## Step 12 — Reliability and ops

**Prompt:**
```
Production hardening.
- Add pino structured logging with request ids; never log tokens or full queries with emails.
- Add Sentry (server + client) via env DSN.
- Add a Dockerfile and docker-compose for local; document deploy to Render/Railway/Fly.
- Add /api/health deep check (db, provider, mail).
- Add a GitHub Actions workflow: install, lint, test, build on PR.
- Add graceful timeouts (scrape 8s, provider 6s) and a circuit breaker for failing providers.
```

**Manual:** deploy, point the domain, enable HTTPS, add UptimeRobot (free) on `/api/health`.

**Done when:** CI is green, Sentry catches a deliberately thrown error, uptime monitor is live.

---

## Step 13 — Admin and business metrics

**Prompt:**
```
Add a minimal admin dashboard at /admin (role='admin' only).
Show: signups/day, active users 7d, searches/day, cost per search (avg), MRR (from subscriptions), free->paid conversion, top queries (anonymized), churned users. Add CSV export.
```

**Done when:** you can read MRR, cost per search and conversion without opening the database. These numbers are also what a buyer will ask for later.

---

## Step 14 — Launch checklist (non-code, track in Cursor as a TODO file)

- [ ] Choose first segment (medtech/healthcare or fintech) and finish its source pack
- [ ] Build the 10-question benchmark and record before/after vs Google (screenshots)
- [ ] Razorpay live mode KYC done
- [ ] Legal pages reviewed
- [ ] 20 pilot users invited (free 2 weeks, 20-minute feedback call)
- [ ] 3 public "industry briefing" posts generated with Gyanwire
- [ ] Founding-customer offer (₹999-2,999/mo) sent to 5 small teams
- [ ] First 10 paying customers, then review retention before spending on ads

---

## Suggested order and rough effort

| Phase | Steps | Effort (solo, evenings/weekends) |
|---|---|---|
| Make it chargeable | 0-3 | 1-2 weeks |
| Make it good and safe | 4-6 | 1-2 weeks |
| Make it sticky | 7-9 | 2 weeks |
| Make it scalable | 10-13 | 2 weeks |
| Sell | 14 | ongoing |

If you only have time for a minimum viable paid version: **Steps 0, 1, 2, 3, 5, 7, 11.** The rest can follow once you have paying users.

---

## Cursor working habits that save time

- Keep each chat scoped to one step; start a new chat per step to avoid context drift.
- After every step, ask Cursor: `Review the diff of this branch for security issues (auth, input validation, secrets, SQL injection) and list anything risky.`
- Ask for tests with each feature, and run them before merging.
- When Cursor changes the architecture, have it update `ARCHITECTURE.md` in the same PR. A current doc speeds up due diligence if you sell the business.
