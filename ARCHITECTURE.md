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
| **Account** | Email/password or Google sign-in; profile in Pinia; JWTs in httpOnly cookies only. |
| **Monetization** | Free daily search limits; Pro/Team via Razorpay; usage shown in the workspace. |
| **Quality control** | Rank with Gyanwire’s own pointers (trusted domains, practical terms, category boosts). |

### Non-goals (current)

- Not a full browser or content CMS  
- Not a medical diagnosis / clinical decision tool  
- Not a filmic multi-act marketing scroll page (grammar is **live surface**)  
- Steps 4–14 in `CURSOR_PLAN.md` (provider abstraction, source packs, projects, alerts, etc.) are planned, not all shipped yet  

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
   ScrapeService    ──► extract main text (Jsoup)
        │
        ▼
   PointersService  ──► score 0–100 + why-line
        │
        ▼
   ranked findings ──► chat turn (ResultsPanel)
```

**Score meaning in UI:** left number = list rank; amber badge = pointer match score (0–100).

### 4.5 Data model (Postgres)

| Table | Role |
|---|---|
| `users` | UUID PK, email, password_hash, profile, `role` default `user`, `deleted_at` |
| `refresh_tokens` | Session refresh tokens, soft-delete via `deleted_at` |
| `plans` | Seeded `free` / `pro` / `team` limits + feature flags |
| `subscriptions` | User ↔ plan, Razorpay ids, status, period end |
| `usage_events` | Metered actions (`search`); user or anonymous `ip_hash` |
| `billing_events` | Razorpay event ids for idempotent webhooks |
| `research_industries` / `research_industry_subs` | UI catalog |
| `gyanwire_flyway_schema_history` | Flyway history |

Migrations run on Spring Boot startup (Flyway).

### 4.6 Data & security

| Concern | Approach |
|---|---|
| Persistence | `SPRING_DATASOURCE_*` JDBC + Flyway |
| RLS | Enabled on product tables; no `anon`/`authenticated` policies |
| Users | Own schema only — never shared with patient-platform DBs |
| Sessions | JWT access + refresh; refresh cookie path `/api/auth` |
| Plan limits | Search metering → HTTP 402 `LIMIT_REACHED` + `/pricing` |
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
| `LLM_*` / `SEARXNG_URL` | Optional query sharpen / discovery |
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

---

## 6. Related docs

- `BRIEF.md` — vibe, feeling curve, signature move, aesthetic  
- `CURSOR_PLAN.md` — step-by-step path from tool → paid product  
- `README.md` — quick start and env overview  
- `FINGERPRINTS.md` — design fingerprint registry  
- `.cursor/rules/gyanwire.mdc` — always-on coding conventions  
