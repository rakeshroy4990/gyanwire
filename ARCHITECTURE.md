# Gyanwire — Architecture

## 1. What’s this site about?

**Gyanwire** is an India-first R&D research workbench. Visitors pick a research industry (and optional sub-topic), write messy thoughts or a question, and get a short ranked list of useful web pages — with a plain “why this matched” line for each result.

It is a **live product surface**, not a marketing site: the page *is* the tool (compose → search → findings). Research behaves like a **chat thread**: earlier questions stay visible, findings attach under each turn, and the composer stays on top for follow-ups.

Optional sign-in ties the session to a **Gyanwire-owned** Postgres `users` table. Plans (Free / Pro / Team) meter daily searches; paid upgrades go through **Razorpay Subscriptions**.

**Tell-someone sentence:**  
It’s the site where you dump messy research thoughts and get the best pages from the web, ranked for what you meant.

---

## 2. Core purpose

| Goal | Description |
|---|---|
| **Primary** | Turn industry + free-form thoughts into a small set of high-signal web findings (papers, products, news, labs). |
| **Secondary** | Surface industry product news quickly when a category is selected (before a deep research chat). |
| **From news to a plan** (`CURSOR_PLAN_FLOW_FIRST.md`, not shipped) | After search streaming and a cited brief: profile-fit ideas, a 10% skill budget, a 12-week plan, and a one-page outline. Scores and rupee amounts come from code. The current LLM only phrases them. |
| **Account** | Email/password or Google sign-in; profile in Pinia; JWTs in httpOnly cookies only. |
| **Monetization** | Free daily search limits; Pro/Team via Razorpay; usage shown in the workspace. |
| **Quality control** | Rank with Gyanwire’s own pointers (trusted domains, practical terms, category boosts). |

### Non-goals (current)

- Not a full browser or content CMS  
- Not a medical diagnosis / clinical decision tool  
- Not a filmic multi-act marketing scroll page (grammar is **live surface**)  
- Steps 4–14 in `CURSOR_PLAN.md` are historical. The active sequence is `CURSOR_PLAN_FLOW_FIRST.md` (S0–S16), not shipped yet  
- Temperature stays `0.2` and responses stay JSON. `ModelRouter` is in place; embeddings and agents stay parked in `docs/PARKED_MODEL_WORK.md`  
- Share Market ideas stay education/tools only (no tips or signals). Medical ideas stay education/admin/logistics only (no diagnosis or treatment claims)  
- Not a store of exact salary, Aadhaar, PAN, or bank details

---

## 3. Functional points

### 3.1 Research workspace (chat)

1. **Industry selection** — Share Market, IT, Medical, Space, Social Media, Gaming, Astrology (server catalog when available).  
2. **Sub-topic selection** — Narrows browse-news within the industry.  
3. **Chat composer (sticky on top)** — New question input; Enter sends, Shift+Enter newline.  
4. **Live query preview** — Signature move: shows the likely R&D query before commit.  
5. **Research thread** — Each sent question becomes a turn; findings attach under that turn and stay when the user asks again.  
6. **Browse news** — Industry/sub clicks load product news only while the thread is empty.  
7. **Deep research** — `POST /api/search` runs discover → scrape → pointer ranking (plan-limited).  
8. **Findings** — Rank (1…n), title, host, why-line, match score (0–100).  
9. **Skeleton loading** — Findings area shows shimmer skeletons while news/research loads (no loading text in the header).  
10. **Usage meter** — “N of M searches left today”; 402 shows an upgrade prompt.  
11. **New chat / home** — Clears thread; header brand returns to the home workspace.

### 3.2 Authentication

1. **Sign in / Register modals** — Email/password + Google (GIS token client).  
2. **Session** — Access + refresh JWTs in **httpOnly cookies**; profile in **Pinia** + `sessionStorage` (never `localStorage` for tokens).  
3. **Header** — Brand + Pricing nav · Sign in or avatar / Account menu.  
4. **Own identity store** — Postgres `users` / `refresh_tokens` via `DATABASE_URL` (UUID ids, `role` default `user`, soft delete via `deleted_at`).

### 3.3 Billing & plans

1. **Plans** — `free` (5 searches/day), `pro` (100), `team` (500); anonymous IP cap = 2/day.  
2. **Checkout** — Razorpay Subscriptions (Checkout.js) from `/pricing`.  
3. **Webhooks** — Signed `POST /api/billing/webhook`; events stored for idempotency.  
4. **Cancel** — Cancel at period end from Account menu.  
5. **Success** — `/billing/success` refreshes plan from `/api/me/usage`.

### 3.4 Routes (Vue Router)

| Path | Page | Notes |
|---|---|---|
| `/` | Research workspace | Default live surface |
| `/pricing` | Free / Pro / Team cards | Monthly/annual toggle |
| `/billing/success` | Post-checkout | Refreshes plan + usage |

### 3.5 Auth API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Email/password login |
| `POST` | `/api/auth/google-login` | Google access token → session |
| `POST` | `/api/auth/register` | Create user |
| `POST` | `/api/auth/refresh` | Rotate tokens |
| `POST` | `/api/auth/logout` | Revoke refresh + clear cookies |
| `GET` | `/api/auth/me` | Current profile |

### 3.6 Research / usage / billing API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Engine, auth, billing readiness |
| `GET` | `/api/industries` | Industry catalog (gyanwire-server; client caches in IndexedDB) |
| `GET` | `/api/news/default` / `/api/news/:industry` | Product news feed |
| `POST` | `/api/search` | Deep research (optional auth + plan limit + usage log) |
| `GET` | `/api/me/usage` | Plan + remaining daily searches |
| `POST` | `/api/billing/checkout` | Create Razorpay subscription |
| `POST` | `/api/billing/webhook` | Razorpay signed webhooks (raw body) |
| `POST` | `/api/billing/cancel` | Cancel at period end |
| `GET` | `/api/billing/status` | Subscription status |

### 3.7 Flow API (`CURSOR_PLAN_FLOW_FIRST.md`)

Search streams on `POST /api/search/stream` (`status`, `finding`, `done`, `error`) and still accepts JSON `POST /api/search`. Profile, ideas, skill and weekly plans, outlines, projects, source-pack toggles, saved queries, referrals, industry mode, claim check, and `POST /api/mcp` (header `X-Api-Key`) are on the server. Scores, rupees, and week counts are computed in Java. The configured model is unchanged.

| Method | Path | Step | Purpose |
|---|---|---|---|
| `POST` | `/api/search/stream` | S4 | SSE findings (`status`, `finding`, `done`, `error`). JSON `POST /api/search` stays |
| `GET` / `PUT` | `/api/me/profile` | S7 | Profile v2. Income as a band. `consent_at` required |
| `DELETE` | `/api/me/profile/data` | S7 | Delete profile and skills only |
| `POST` | `/api/ideas/from-news` | S9 | `{ url, title, description, industry, sub }` → ranked ideas |
| `GET` | `/api/ideas` | S9 | Saved ideas for the user |
| `POST` | `/api/ideas/{id}/feedback` | S9 | Thumbs and reason |
| `POST` | `/api/plans/skill` | S10 | 10% skill budget for an idea (weeks include `taskType`, `sittings`, `baseHours`) |
| `POST` | `/api/plans/weekly` | S11 | 12-week plan (Pro/Team) |
| `POST` | `/api/plans/checkin` | S11 | `done` / `partly` / `not_done` |
| `POST` / `GET` | `/api/plans/business-outline` | S11 | Nine-section outline. Markdown download |
| `GET` | `/api/options` | Creative UI | Extended V12 catalog with `taskFit` speedup ranges (IndexedDB-cached) |
| `POST` | `/api/plan/actuals` | Creative UI | Log actual hours for calibration |
| `POST` | `/api/plan/variants/meter` | Creative UI | Gate multi-variant compare (`max_plan_variants`) |
| MCP | `search_research`, `get_findings`, `generate_idea` | S14 | API key. Inputs treated as untrusted |

Planned pages: `/profile`, `/plan`, `/roadmap`, a business-outline page, `/terms`, `/privacy`, `/refund`, `/contact`. The Idea button sits on each row in `ResultsPanel.vue`. Metering kinds: `search` (shipped), `brief` (1/day on Free), `idea` (Free 3/day, Pro 30, Team 150). Over limit → HTTP 402 `LIMIT_REACHED`. Weekly plan and outline require `can_roadmap`.

---

## 4. Technical architecture

### 4.1 High-level diagram

```text
┌──────────────────────────────────────────────────────────────┐
│  Browser (Vue 3 + Pinia + Vue Router + Vite)                 │
│  http://localhost:5180                                       │
│                                                              │
│  AppHeader (brand · Pricing · auth)                          │
│  Routes: / · /pricing · /billing/success                     │
│  Research chat thread + FindingsSkeleton                     │
│  stores: authSession · authForm · ui                         │
│  composables: useAuth · useResearch · useUsage · useBilling  │
└────────────────────────────┬─────────────────────────────────┘
                             │  /api/*  (credentials)
                             ▼
┌──────────────────────────────────────────────────────────────┐
│  gyanwire-server (Spring Boot)                               │
│  http://localhost:8080                                       │
│                                                              │
│  auth · industries · news · search · usage · billing         │
│  Flyway + JPA · JWT httpOnly cookies                         │
└────────────────────────────┬─────────────────────────────────┘
                             │
                             ▼
              Gyanwire Postgres
              Live web sources · Razorpay · optional LLM
```

### 4.2 Frontend architecture

```text
client/src/
  main.js · App.vue · router/index.js
  pages/
    ResearchPage.vue
    PricingPage.vue
    BillingSuccessPage.vue
  stores/
    authSession.store.js   # profile (Pinia + sessionStorage)
    authForm.store.js      # login/register fields
    ui.store.js            # modals + homeNonce (header → reset workspace)
  services/
    auth.service.js
    googleSignIn.service.js
    usage.service.js
    billing.service.js     # checkout + Razorpay Checkout.js
    industries.service.js  # IndexedDB-first research industries
    indexedDb.js
    apiBase.js             # VITE_BACKEND_URL (local vs Cloud Run)
  composables/
    useAuth.js
    useResearch.js         # chat thread, browse news, search
    useUsage.js            # plan quota UI + 402 handling
    useBilling.js
  components/
    layout/AppHeader.vue
    auth/LoginModal.vue · RegisterModal.vue
    research/
      ResearchWorkspace.vue
      ResearchPanel.vue      # hero, industries, composer, thread
      ResultsPanel.vue
      FindingsSkeleton.vue   # loading placeholders
  styles.css                 # forest/bone/amber live-surface tokens
```

**UI / UX rules (current):**

- Grammar: **live surface** (see `BRIEF.md` / Scrollcraft skill) — not a filmic landing page  
- Tokens: deep forest canvas, bone ink, amber accent; Fraunces + DM Sans  
- Content column ~1080px with generous vertical rhythm  
- Header: brand · divider · Pricing nav · actions (no loading text in header)  
- Loading: skeleton findings, not header status strings  
- Components never call APIs directly — composables → services  

**Auth rules:**

- JWT access/refresh → httpOnly cookies only  
- Profile → Pinia `authSession`  
- Form state → Pinia `authForm`  

### 4.3 Backend architecture

```text
gyanwire-server/
  src/main/java/com/gyanwire/
    auth/          # JWT cookies, Google login, register/login/refresh
    billing/       # Razorpay checkout + webhook
    usage/         # plan limits + /api/me/usage
    research/      # news + discover/scrape/pointers/LLM search
    llm/           # OpenAiClient is the only /chat/completions POST. Flag off: LlmClient + gpt-4o-mini. Flag on: LlmService tiers Luna/Sol.
    persistence/   # JPA entities + repositories
    controller/    # industries, health, API envelope
  src/main/resources/db/migration/   # Flyway V1+
```

### 4.4 Search engine pipeline

```text
Chat message + industry (+ optional sub)
        │
        ▼
   (optional LLM query sharpen)
        │
        ▼
   DiscoverService  ──► candidate URLs
        │              (DDG / Wikipedia / SearXNG / seeds)
        ▼
   ScrapeService    ──► UrlGuard (public http/https, no private or metadata IPs,
                     max 3 redirects re-checked, 2 MB, 8 s)
                  ──► robots.txt + per-host pause
                  ──► visible text only; model sees it inside <page_content>
        │
        ▼
   PointersService  ──► score 0–100 + why-line
        │
        ▼
   ranked findings ──► chat turn (ResultsPanel)
```

**Score meaning in UI:** left number = list rank; amber badge = pointer match score (0–100).

With `llm.router.enabled=false` (the Cloud Run default), the only model HTTP call is still `LlmClient.complete`: temperature `0.2`, `response_format: json_object`, model `gpt-4o-mini`. `ModelRouter` can send `idea` and `outline` to `LLM_MODEL_HIGH` for Pro and Team; that slot stays equal to `gpt-4o-mini`.

With the router on, callers pass a tier to `com.gyanwire.llm.LlmService`. `OpenAiClient` is the only `/chat/completions` POST. `LIGHT` is `gpt-6-luna` (effort low). `MAIN` and `DEEP` are both `gpt-6.1-sol` (medium and high). Those requests omit temperature and send `reasoning_effort`. `CostCalculator` writes `cost_inr` on `llm_calls` (V23). Over 80% of the ₹300 daily cap, `MAIN` drops to `LIGHT`. At 100%, optional stages return cache or a template. Anonymous gets no model call. Free stays on Luna. A shared plan draft is cached in `plan_cache` (V24). Stage flags default off, so a push does not change production answers until one stage is enabled.

### 4.4b Planned flow (S0–S16, not shipped)

News and finding ids (`n-1`, `r-1`) are not stored. Idea generation sends `{ url, title, description, industry, sub }`. Industry keys are catalog names (`Share Market`, `IT`, `Medical`, `Space`, `Social Media`, `Gaming`, `Astrology`). Full page text is not kept permanently. S5 may cache snippet plus extracted text for 7 days in `page_cache`.

```text
ScrapeService + UrlGuard
        │
        ▼
   pointers + page_cache full-text + freshness   (RRF, no embeddings)
        │
        ▼
   SSE findings → Cited Brief (citations required)
        │
        ▼
   news_signals (cached by url hash)
        │
        ▼
   PatternMatcher → IdeaGenerator (LlmClient wording)
        │
        ▼
   FitFilter → IdeaScorer (Java weights, breakdown JSON)
        │
        ▼
   skill budget (10% of income-band proxy, free first)
        │
        ▼
   12-week plan → 9-section business outline
```

`skill_budget_month = incomeProxy(band) × invest_pct / 100` (default 10%, range 5–15). Band proxies: `0` → ₹0, `under_15k` → ₹7,500, `15_30` → ₹22,500, `30_50` → ₹40,000, `50_100` → ₹75,000, `over_100` → ₹1,00,000. A paid catalog row replaces its free alternative only when it saves at least 4 weeks. Subscriptions carry `cancel_by`. Affiliate links stay off.

### 4.5 Data model (Postgres)

| Table | Role |
|---|---|
| `users` | UUID PK, email, password_hash, profile, `role` default `user`, `deleted_at` |
| `refresh_tokens` | Session refresh tokens, soft-delete via `deleted_at` |
| `plans` | Seeded `free` / `pro` / `team` limits + feature flags |
| `subscriptions` | User ↔ plan, Razorpay ids, status, period end |
| `usage_events` | Metered actions (`search`; planned kinds `brief`, `idea`); user or anonymous `ip_hash` |
| `billing_events` | Razorpay event ids for idempotent webhooks |
| `research_industries` / `research_industry_subs` | UI catalog |
| `gyanwire_flyway_schema_history` | Flyway history (RLS after migrate via `FlywayHistoryRlsLockdown`; not inside V18 — would deadlock) |
| `llm_calls` | V6. Feature, prompt version, tokens, latency. Does not change the request |
| `page_cache` | Planned V7. Snippet + text + `tsvector`, 7-day TTL. No embeddings |
| `finding_passages` | Planned V8. Passages cited by a brief |
| `user_profiles` / `user_skills` | Planned V9. Persona, income **band**, hours, consent |
| `news_signals` | Planned V10. One JSON signal per URL hash |
| `idea_runs` / `ideas` | Planned V11. Score breakdown from Java |
| `tools` / `courses` / `skill_graph` | Planned V12. INR prices, `last_verified_at`, `affiliate` false |
| `weekly_plans` / `weekly_tasks` / `business_outlines` | Planned V13 |
| `projects` / `project_items` | Planned V14 |
| `source_packs` / `source_pack_domains` | Planned V15 |
| `saved_queries` / `digests` | Planned V16. Scheduled mail, not an agent |

`plans` gains `daily_idea_limit` and `can_roadmap` with the idea step. New tables use the V5 RLS lockdown (enable RLS, revoke from `PUBLIC` and from `anon` / `authenticated` / `authenticator` when those roles exist).

Migrations run on Spring Boot startup (Flyway).

### 4.6 Data & security

| Concern | Approach |
|---|---|
| Persistence | `SPRING_DATASOURCE_*` JDBC + Flyway |
| RLS | Enabled on product tables and `gyanwire_flyway_schema_history`; no `anon`/`authenticated` policies |
| Users | Own schema only — never shared with patient-platform DBs |
| Sessions | JWT access + refresh; refresh cookie path `/api/auth` |
| Plan limits | Search metering → HTTP 402 `LIMIT_REACHED` + `/pricing`. Planned brief, idea, and roadmap gates use the same 402 |
| Billing | Razorpay keys; webhook HMAC on raw body |
| Secrets | Root `.env`; Vite `envDir` = project root for `VITE_*` |
| CORS | `APP_CORS_ALLOWED_ORIGIN_PATTERNS` + credentials |

### 4.7 Key environment variables

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_*` | Product Postgres JDBC |
| `JWT_SECRET` | Access/refresh signing |
| `VITE_GOOGLE_OAUTH_CLIENT_ID` | Google Sign-In |
| `RAZORPAY_KEY_ID` / `KEY_SECRET` / `WEBHOOK_SECRET` | Billing |
| `RAZORPAY_PLAN_PRO_MONTHLY` (etc.) | Razorpay plan ids |
| `LLM_*` / `SEARXNG_URL` | `LLM_MODEL` is the low model and the rollback value. `LLM_MODEL_HIGH` is idea and outline for Pro and Team. Temperature stays `0.2`. |
| `VITE_BACKEND_URL` | API origin baked into the UI (`http://localhost:8080` local, Cloud Run in prod) |

See `.env.example` for the full list.

### 4.8 Runtime ports

| Surface | Default | Notes |
|---|---|---|
| UI (Vite) | `5180` | Proxy `/api` → gyanwire-server |
| API (Spring) | `8080` | `GYANWIRE_SERVER_PORT` / Cloud Run `PORT` |
| Local Docker Postgres | `5433` | `npm run db:up` |

### 4.9 Testing & production

```bash
npm test                 # gyanwire-server Gradle tests
npm run build            # Vite → dist/
npm run gyanwire-server  # Spring Boot API (+ serves dist/ when present)
```

Deploy helpers: Cloud Run / Firebase UI scripts under `scripts/`, `Dockerfile`, `cloudbuild.yaml`.

---

## 5. Key design decisions

1. **Own ranking pointers** over opaque third-party “search APIs as product.”  
2. **Live surface + research chat** — composer on top, thread keeps questions and findings.  
3. **Separate auth database** — product-owned Postgres; numbered Flyway-style SQL migrations.  
4. **Plan metering before scale** — Free/Pro/Team limits and Razorpay subscriptions early.  
5. **Cookies for tokens** — browser JS never stores JWTs in `localStorage`.  
6. **Skeleton loading in content** — never use the header as a status ticker.  
7. **Scrollcraft taste on a tool** — forest/bone/amber, Fraunces + DM Sans; signature move = likely-search preview (`BRIEF.md`).  
8. **Deterministic score, LLM for wording only** — idea rank, rupee amounts, and week lists come from code and the catalog. The current LLM may phrase titles, why-lines, briefs, and outline prose, and only from stripped fields (no name, email, or exact income). Same inputs produce the same ranking.  
9. **One client, two model slots** — temperature stays `0.2` and `response_format` stays `json_object`. `ModelRouter` picks the model. The bakeoff in `eval/reports/` decides whether `LLM_MODEL_HIGH` differs from `LLM_MODEL`.

---

## 6. Related docs

- `BRIEF.md` — vibe, feeling curve, signature move, aesthetic  
- `CURSOR_PLAN.md` — historical path from tool → paid product (Steps 0–14)  
- `CURSOR_PLAN_FLOW_FIRST.md` — active sequence S0–S16 under model freeze  
- `docs/PARKED_MODEL_WORK.md` — model upgrades P1–P6, blocked until S16
- `README.md` — quick start and env overview  
- `FINGERPRINTS.md` — design fingerprint registry  
- `.cursor/rules/gyanwire.mdc` — always-on coding conventions  
