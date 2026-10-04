# Gyanwire

Pick a few categories, write what you are thinking, and get the best matching pages from the live web.

Architecture overview (purpose, functional points, tech): see [ARCHITECTURE.md](./ARCHITECTURE.md).

Search is powered by **Gyanwire’s own engine** (no Firecrawl):

1. **Discover** candidate pages
2. **Scrape** useful page text
3. **Rank** with your custom pointers in `server/engine/pointers.js`

An optional LLM can sharpen the query and lightly nudge rankings.

## Quick start

```bash
cd /Users/rakeshroy/Documents/Projects/Gyanwire
cp .env.example .env
npm install
npm run ui
```

`npm run ui` frees ports `5180` / `3010` if they are busy, then starts both UI and API.

Then open:

- App UI: http://localhost:5180
- API: http://localhost:3010

## Environment

| Variable | Required | Purpose |
|---|---|---|
| `LLM_API_KEY` | No | Query refinement + ranking nudge |
| `LLM_BASE_URL` | No | OpenAI-compatible API base (`https://api.openai.com/v1`) |
| `LLM_MODEL` | No | Model name (`gpt-4o-mini` by default) |
| `UI_PORT` | No | Vite UI port (`5180` by default) |
| `PORT` | No | API port (`3010` by default) |
| `APP_PERSISTENCE_PROVIDER` | Yes for auth | Set to `postgres` |
| `SPRING_DATASOURCE_URL` | Yes for auth | JDBC URL (`jdbc:postgresql://...`) |
| `SPRING_DATASOURCE_USERNAME` | Yes for auth | Postgres username |
| `SPRING_DATASOURCE_PASSWORD` | Yes for auth | Postgres password |
| `JWT_SECRET` | Yes for auth | Signing secret for access/refresh JWTs |
| `VITE_GOOGLE_OAUTH_CLIENT_ID` | No | Google Sign-In (GIS) OAuth web client ID |

## Auth (Sign in + Google)

Sign-in follows the hospital frontend architecture adapted to Gyanwire:

- Vue 3 + Pinia (`authSession` / `authForm` stores) for user profile everywhere
- Sign-in / register modals in the UI
- Email/password + Google (Gmail) via Google Identity Services
- Express `/api/auth/*` with httpOnly cookies
- Shared Supabase `users` + `refresh_tokens` tables

Auth uses the shared Supabase `users` / `refresh_tokens` tables (same DB as hospital).
Configure the same Spring datasource variables:

```bash
APP_PERSISTENCE_PROVIDER=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=...
AUTH_AUTO_MIGRATE=false
```

```bash
npm run ui
```

Set `VITE_GOOGLE_OAUTH_CLIENT_ID` in `.env` and add `http://localhost:5180` as an Authorized JavaScript origin in Google Cloud Console.

## Own pointers

Edit `server/engine/pointers.js` to change how pages are scored:

- `POINTER_WEIGHTS` — importance of each signal
- `CATEGORY_POINTERS` — boost terms per category
- `TRUSTED_DOMAIN_HINTS` / `LOW_QUALITY_DOMAIN_HINTS` — domain preferences
- `PRACTICAL_TERMS` — words that mark actionable pages

## Discovery sources

1. DuckDuckGo HTML results (default, no key)
2. Wikipedia OpenSearch
3. Optional SearXNG (`SEARXNG_URL`)
4. Category seed pages as a last-resort fallback

## Engine layout

```
server/engine/
  discover.js   # find candidate URLs
  scrape.js     # fetch + extract main text
  pointers.js   # your ranking rules
  search.js     # discover → scrape → score
```

## Production

```bash
npm run build
npm start
```

## Design notes

Built with scrollcraft **live surface** grammar: the page *is* the product UI.
Status chrome, compose panel, and results panel are the whole experience.
