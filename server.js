const express = require('express');
const { Pool } = require('pg');
const crypto = require('crypto');

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '5mb' }));

const PORT = process.env.PORT || 8080;
const API_TOKEN = process.env.WHIM_API_TOKEN;
const DATABASE_URL = process.env.DATABASE_URL;
if (!API_TOKEN) console.warn('WARNING: WHIM_API_TOKEN is unset; API requests will be rejected.');
if (!DATABASE_URL) console.warn('WARNING: DATABASE_URL is unset; database endpoints will not work.');

const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.PGSSLMODE === 'disable' ? false : { rejectUnauthorized: false }
}) : null;

async function initDb() {
  if (!pool) return;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS whim_inventory (
      tenant_id TEXT PRIMARY KEY,
      payload JSONB NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}
function authorized(req, res, next) {
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!API_TOKEN || !supplied ||
      supplied.length !== API_TOKEN.length ||
      !crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(API_TOKEN))) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
}
app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'whim-cloud-api' }));
app.get('/api/inventory/:tenantId', authorized, async (req, res) => {
  if (!pool) return res.status(503).json({ error: 'Database is not configured' });
  const tenantId = String(req.params.tenantId || '');
  if (!/^[a-zA-Z0-9._@-]{1,160}$/.test(tenantId)) return res.status(400).json({ error: 'Invalid tenant ID' });
  try {
    const result = await pool.query('SELECT payload, updated_at FROM whim_inventory WHERE tenant_id = $1', [tenantId]);
    if (!result.rowCount) return res.status(404).json({ error: 'No cloud inventory saved yet' });
    res.json({ tenantId, payload: result.rows[0].payload, updatedAt: result.rows[0].updated_at });
  } catch (e) { console.error(e); res.status(500).json({ error: 'Database operation failed' }); }
});
app.put('/api/inventory/:tenantId', authorized, async (req, res) => {
  if (!pool) return res.status(503).json({ error: 'Database is not configured' });
  const tenantId = String(req.params.tenantId || '');
  if (!/^[a-zA-Z0-9._@-]{1,160}$/.test(tenantId)) return res.status(400).json({ error: 'Invalid tenant ID' });
  if (!req.body || typeof req.body.payload !== 'object' || req.body.payload === null || Array.isArray(req.body.payload))
    return res.status(400).json({ error: 'payload must be a JSON object' });
  try {
    const result = await pool.query(
      `INSERT INTO whim_inventory (tenant_id, payload, updated_at) VALUES ($1, $2::jsonb, NOW())
       ON CONFLICT (tenant_id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = NOW()
       RETURNING updated_at`,
      [tenantId, JSON.stringify(req.body.payload)]
    );
    res.json({ saved: true, tenantId, updatedAt: result.rows[0].updated_at });
  } catch (e) { console.error(e); res.status(500).json({ error: 'Database operation failed' }); }
});
app.use((_req, res) => res.status(404).json({ error: 'Not found' }));
initDb().then(() => app.listen(PORT, '0.0.0.0', () => console.log(`WHIM cloud API listening on ${PORT}`)))
  .catch(e => { console.error('Database initialization failed:', e); process.exit(1); });
