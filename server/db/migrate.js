import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { getPool } from './pool.js';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export async function migrate() {
  const sqlPath = path.join(__dirname, 'schema.sql');
  const sql = await fs.readFile(sqlPath, 'utf8');
  const pool = getPool();
  await pool.query(sql);
}

const isCli = process.argv[1]
  && path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1]);

if (isCli) {
  migrate()
    .then(() => {
      console.log('Gyanwire schema migrated.');
      process.exit(0);
    })
    .catch((err) => {
      console.error('Migration failed:', err.message);
      process.exit(1);
    });
}
