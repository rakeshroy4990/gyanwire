# Gyanwire — Architecture

## 1. What’s this site about?

**Gyanwire** is an India-first R&D research workbench. Visitors pick a research industry (and optional sub-topic), write messy thoughts or a question, and get a short ranked list of useful web pages — with a plain “why this matched” line for each result.

It is a **live product surface**, not a marketing site: the page *is* the tool (compose → search → findings). Optional sign-in ties the session to a **Gyanwire-owned** Postgres `users` table (separate from any patient-platform database).

**Tell-someone sentence:**  
It’s the site where you dump messy research thoughts and get the best pages from the web, ranked for what you meant.

---

## 2. Core purpose

| Goal | Description |
|---|---|
| **Primary** | Turn industry + free-form thoughts into a small set of high-signal web findings (papers, products, news, labs). |
| **Secondary** | Surface industry product news quickly when a category is selected (before a deep research query). |
| **Account** | Let users sign in (email/password or Google) so identity is consistent across the product surface. |
| **Quality control** | Rank with Gyanwire’s own pointers (trusted domains, practical terms, category boosts) — not a generic scrape dump. |

### Non-goals (current)

- Not a full browser or content CMS  
- Not a medical diagnosis / clinical decision tool  
- Not a multi-page marketing funnel  

---

## 3. Functional points

### 3.1 Research workspace

1. **Industry selection** — Share Market, IT, Medical, Space, Social Media (and server-driven catalog when available).  
2. **Sub-topic selection** — Narrows news/research within the industry.  
3. **Thoughts composer** — Free-text research note (min clarity check before deep search).  
4. **Live query preview** — Shows the likely R&D query string before the user commits.  
5. **Industry news feed** — On industry/sub click, loads latest product-oriented findings.  
6. **Deep research** — `POST /api/search` runs discover → scrape → pointer ranking → results list.  
7. **Findings panel** — Rank, title, host, why-line, score.

### 3.2 Authentication

1. **Sign in modal** — Email/password + Continue with Google (GIS token client).  
2. **Register modal** — Create account (email/password); Google also available.  
3. **Session** — Access + refresh JWTs in **httpOnly cookies**; profile in **Pinia** + `sessionStorage` (no JWT in `localStorage`).  
4. **Header chrome** — Signed-out: Sign in. Signed-in: avatar/name/role + Sign out.  
5. **Personalized copy** — Welcome greeting uses `authSession` when authenticated.  
6. **Own identity store** — Reads/writes Gyanwire Postgres `users` / `refresh_tokens` via `GYANWIRE_DATABASE_URL` (UUID ids, `role` default `user`, soft delete via `deleted_at`).

### 3.3 Auth API (functional contracts)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Email/password login (`EmailId`, `Password`) |
| `POST` | `/api/auth/google-login` | Google access token → userinfo → session |
| `POST` | `/api/auth/register` | Create Gyanwire user row |
| `POST` | `/api/auth/refresh` | Rotate tokens (refresh cookie scoped to `/api/auth`) |
| `POST` | `/api/auth/logout` | Revoke refresh + clear cookies |
| `GET` | `/api/auth/me` | Current user profile |

### 3.4 Research / news API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Engine + auth readiness |
| `GET` | `/api/industries` | Industry catalog |
| `GET` | `/api/news/default` / `/api/news/:industry` | Product news feed |
| `POST` | `/api/search` | Deep research pipeline |

---

## 4. Technical architecture

### 4.1 High-level diagram

```text
┌─────────────────────────────────────────────────────────────┐
│  Browser (Vue 3 + Pinia + Vite)                             │
│  http://localhost:5180                                      │
│                                                             │
│  AppHeader · ResearchWorkspace · Login/Register modals      │
│  stores: authSession · authForm · ui                        │
│  composables: useAuth · useResearch                         │
│  Google Identity Services (token client)                    │
└───────────────────────────┬─────────────────────────────────┘
                            │  /api/*  (Vite proxy, credentials)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Express API (Node)                                         │
│  http://localhost:3010                                      │
│                                                             │
│  routes/auth.js  → services/auth/*  → PostgreSQL            │
│  routes/search.js → engine + news/find/llm services         │
└───────────────┬─────────────────────────────┬───────────────┘
                │                             │
                ▼                             ▼
     Gyanwire Postgres                 Live web sources
     (own DB / Supabase or Neon)       DuckDuckGo · Wikipedia
     users / refresh_tokens            · optional SearXNG · scrape
     GYANWIRE_DATABASE_URL             · optional LLM refine
```

### 4.2 Frontend architecture

```text
client/src/
  main.js                 # createApp + Pinia + hydrate AuthSession
  App.vue
  stores/
    authSession.store.js  # user profile (Pinia + sessionStorage)
    authForm.store.js     # login/register fields + errors
    ui.store.js           # popup + status line
  services/
    auth.service.js       # API calls, finalizeLoginSession, bootstrap
    googleSignIn.service.js
  composables/
    useAuth.js            # single surface for components
    useResearch.js        # industries, news, search state
  components/
    layout/AppHeader.vue
    auth/LoginModal.vue
    auth/RegisterModal.vue
    research/*
```

**Auth rules:**

- JWT access/refresh → httpOnly cookies only  
- Profile → Pinia `authSession` (shown everywhere via `useAuth`)  
- Form state → Pinia `authForm`  
- Components do not call auth APIs directly; they go through composable/service  

### 4.3 Backend architecture

```text
server/
  index.js                # Express app, CORS+credentials, cookie-parser
  db/
    pool.js               # GYANWIRE_DATABASE_URL → pg Pool
    migrate.js            # numbered migrations in db/migrations/
    migrations/001_init.sql
  routes/
    auth.js               # /api/auth/*
    search.js             # /api/search, news, industries
  services/auth/
    authService.js        # login, google, register, refresh, logout, me
    userRepository.js     # Gyanwire-owned `users` table
    refreshTokenRepository.js
    jwt.js · cookies.js · password.js · google.js
  scripts/
    export-gyanwire-users.js  # one-off copy from a legacy shared DB
  engine/
    discover.js → scrape.js → pointers.js → search.js
  services/
    industryNews.js · find.js · llm.js
```

### 4.4 Search engine pipeline

```text
Thoughts + industry
        │
        ▼
   (optional LLM query sharpen)
        │
        ▼
   discover.js  ──► candidate URLs
        │              (DDG / Wikipedia / SearXNG / seeds)
        ▼
   scrape.js    ──► extract main text
        │
        ▼
   pointers.js  ──► score (weights, category terms, domain hints)
        │
        ▼
   ranked findings ──► UI ResultsPanel
```

### 4.5 Data & security

| Concern | Approach |
|---|---|
| Persistence | `APP_PERSISTENCE_PROVIDER=postgres` + `GYANWIRE_DATABASE_URL` (fallback: `DATABASE_URL`) |
| Users | Own `users` table (UUID `id`, unique email, `role` default `user`, soft delete via `deleted_at`) — never shared with patient-platform data |
| Sessions | JWT access + refresh; refresh cookie path `/api/auth`; `refresh_tokens` soft-deleted via `deleted_at` |
| Google | GIS access token → Google userinfo → find/create user → cookies |
| Secrets | Root `.env` only; Vite `envDir` = project root for `VITE_*` |
| CORS | Dev: permissive + credentials; Prod: `UI_ORIGIN` |

### 4.6 Runtime ports

| Surface | Default | Notes |
|---|---|---|
| UI (Vite) | `5180` | Proxy `/api` → API; Google JS origin must match |
| API | `3010` | Express |
| Local Docker Postgres | `5433` | Optional; production auth uses Supabase pooler |

### 4.7 Production shape

```bash
npm run build   # Vite → dist/
npm start       # Express serves API + static dist
```

---

## 5. Key design decisions

1. **Own ranking pointers** over opaque third-party “search APIs as product.”  
2. **Live surface UI** — one composition: chrome + compose + findings.  
3. **Separate auth database** — Gyanwire users live in a product-owned Postgres; never share tables with a hospital/patient platform.  
4. **Single connection string** — `GYANWIRE_DATABASE_URL` is the primary credential; numbered SQL migrations own schema changes.  
5. **Cookies for tokens** — browser JS never stores JWTs in `localStorage`.  

---

## 6. Related docs

- `BRIEF.md` — product vibe, energy curve, signature interaction  
- `README.md` — quick start, env table, engine pointers  
- `FINGERPRINTS.md` — design fingerprint notes  
