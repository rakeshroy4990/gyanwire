# gyanwire-server

Spring Boot API for Gyanwire (controller → service → repository → JPA, Flyway-owned schema).

This is the **sole backend**. The Vue client talks only to this service.

## Main routes

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/health` | Readiness |
| `GET` | `/api/industries` | Research industries (DB) |
| `GET` | `/api/news/default`, `/api/news/{industry}` | Product news |
| `POST` | `/api/search` | Deep research + plan limits |
| `POST` | `/api/auth/*` | Login, Google, register, refresh, logout |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/me/usage` | Plan + remaining searches |
| `POST/GET` | `/api/billing/*` | Razorpay checkout / webhook / status |

## Run locally

```bash
# from monorepo root
npm run db:up                 # optional local Postgres on :5433
npm run gyanwire-server       # frees :8080 if busy, then bootRun
npm run ui                    # Vite on :5180, proxies /api → :8080
```

Flyway migrations live in `src/main/resources/db/migration/`.
