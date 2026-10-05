# Gyanwire

India-first R&D research workbench: pick an industry, dump messy notes, get ranked web findings.

## Stack

- **UI:** Vue 3 + Pinia + Vite (`client/`)
- **API:** Spring Boot (`gyanwire-server/`)
- **DB:** PostgreSQL (Flyway migrations in `gyanwire-server`)

## Quick start

```bash
cd /Users/rakeshroy/Documents/Projects/Gyanwire
cp .env.example .env
npm install
npm run db:up                 # optional local Postgres :5433
./scripts/start-all.sh        # frees :5180 + :8080 if busy, starts UI + API
# or: npm run start:all / npm run dev
```

Separate processes if you prefer:

```bash
npm run gyanwire-server       # API :8080 only
npm run ui                    # UI :5180 only
```

Then open http://localhost:5180

## Environment

See [`.env.example`](.env.example). Important:

| Variable | Purpose |
|---|---|
| `GYANWIRE_SERVER_PORT` | Local Spring API port (`8080`) |
| `VITE_BACKEND_URL` | API origin for the UI (`http://localhost:8080` local; Cloud Run in `.env.production` / Cloud Build) |
| `SPRING_DATASOURCE_*` | Postgres JDBC |
| `JWT_SECRET` | Auth signing secret |
| `VITE_GOOGLE_OAUTH_CLIENT_ID` | Google Sign-In |
| `LLM_*` | Optional query refine / ranking nudge |
| `RAZORPAY_*` | Subscriptions |

## Deploy

- UI can ship to Firebase Hosting (`npm run deploy:ui`)
- API image builds Spring Boot jar + `dist/` via [`Dockerfile`](Dockerfile) / [`cloudbuild.yaml`](cloudbuild.yaml)
