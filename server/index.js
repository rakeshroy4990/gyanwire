import path from 'node:path';
import { fileURLToPath } from 'node:url';
import cookieParser from 'cookie-parser';
import cors from 'cors';
import dotenv from 'dotenv';
import express from 'express';
import { isPostgresPersistenceEnabled } from './db/pool.js';
import { migrate } from './db/migrate.js';
import { authRouter } from './routes/auth.js';
import { searchRouter } from './routes/search.js';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const isProd = process.env.NODE_ENV === 'production';
const port = Number(process.env.PORT || 3001);
const host = process.env.HOST || '0.0.0.0';
const uiOrigin = process.env.UI_ORIGIN || `http://localhost:${process.env.UI_PORT || 5180}`;

const app = express();

app.set('trust proxy', 1);
app.use(cors({
  origin: isProd ? uiOrigin : true,
  credentials: true,
}));
app.use(express.json({ limit: '32kb' }));
app.use(cookieParser());

app.get('/api/health', (_req, res) => {
  res.json({
    ok: true,
    engine: 'gyanwire',
    engineReady: true,
    authReady: Boolean(isPostgresPersistenceEnabled() && process.env.JWT_SECRET),
    persistenceProvider: process.env.APP_PERSISTENCE_PROVIDER || null,
    llmConfigured: Boolean(process.env.LLM_API_KEY && !process.env.LLM_API_KEY.includes('your-key')),
  });
});

app.use('/api/auth', authRouter);
app.use('/api', searchRouter);

if (isProd) {
  const dist = path.join(root, 'dist');
  app.use(express.static(dist));
  app.get('*', (_req, res) => {
    res.sendFile(path.join(dist, 'index.html'));
  });
}

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(err.status || 500).json({
    success: false,
    message: err.message || 'Something went wrong. Try again in a moment.',
    errorCode: err.code || 'INTERNAL_ERROR',
    timestamp: new Date().toISOString(),
  });
});

async function start() {
  if (isPostgresPersistenceEnabled()) {
    const autoMigrate = String(process.env.AUTH_AUTO_MIGRATE || '').toLowerCase() === 'true';
    if (autoMigrate) {
      try {
        await migrate();
        console.log('PostgreSQL schema ready.');
      } catch (err) {
        console.error('PostgreSQL migration failed:', err.message);
      }
    } else {
      console.log('Using Gyanwire PostgreSQL (AUTH_AUTO_MIGRATE!=true; run npm run db:migrate).');
    }
  } else {
    console.warn(
      'Postgres auth disabled — set APP_PERSISTENCE_PROVIDER=postgres and GYANWIRE_DATABASE_URL.',
    );
  }

  app.listen(port, host, () => {
    console.log(`Gyanwire API listening on http://${host}:${port}`);
  });
}

start();
