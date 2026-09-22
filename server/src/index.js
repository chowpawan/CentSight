import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { Products, CountryCode } from 'plaid';
import db from './db.js';
import { plaid, plaidErrorBody } from './plaid.js';
import { encrypt, decrypt } from './crypto.js';
import { syncItem, syncAll } from './sync.js';

const app = express();
app.use(cors({ origin: 'http://localhost:5173' }));
app.use(express.json());

// Money that moves between your own accounts isn't "spending".
const NOT_SPENDING = ['TRANSFER_IN', 'TRANSFER_OUT', 'LOAN_PAYMENTS', 'INCOME', 'BANK_FEES_REFUND'];
const notSpendingSql = `COALESCE(t.category,'') NOT IN (${NOT_SPENDING.map(() => '?').join(',')})`;

const MONTHLY_FACTOR = {
  WEEKLY: 52 / 12,
  BIWEEKLY: 26 / 12,
  SEMI_MONTHLY: 2,
  MONTHLY: 1,
  ANNUALLY: 1 / 12,
};

// ---------- Plaid Link ----------

app.post('/api/link/token', async (_req, res) => {
  const { data } = await plaid.linkTokenCreate({
    user: { client_user_id: 'local-user' }, // replace with your real user id once you add auth
    client_name: 'Pocketbook',
    products: [Products.Transactions],
    optional_products: [Products.Liabilities],
    country_codes: [CountryCode.Us],
    language: 'en',
    transactions: { days_requested: 730 },
    ...(process.env.PLAID_WEBHOOK_URL ? { webhook: process.env.PLAID_WEBHOOK_URL } : {}),
  });
  res.json({ link_token: data.link_token });
});

app.post('/api/link/exchange', async (req, res) => {
  const { public_token, institution_name } = req.body;
  if (!public_token) return res.status(400).json({ error_message: 'public_token is required' });

  const { data } = await plaid.itemPublicTokenExchange({ public_token });
  db.prepare(
    'INSERT OR REPLACE INTO items (item_id, access_token, institution_name) VALUES (?, ?, ?)'
  ).run(data.item_id, encrypt(data.access_token), institution_name || null);

  const item = db.prepare('SELECT * FROM items WHERE item_id = ?').get(data.item_id);
  const result = await syncItem(item);
  res.json(result);
});

// ---------- Connections ----------

app.get('/api/items', (_req, res) => {
  res.json(
    db.prepare('SELECT item_id, institution_name, created_at, last_synced_at FROM items').all()
  );
});

app.delete('/api/items/:id', async (req, res) => {
  const item = db.prepare('SELECT * FROM items WHERE item_id = ?').get(req.params.id);
  if (!item) return res.status(404).json({ error_message: 'Connection not found' });
  await plaid.itemRemove({ access_token: decrypt(item.access_token) });
  db.prepare('DELETE FROM transactions WHERE item_id = ?').run(item.item_id);
  db.prepare('DELETE FROM items WHERE item_id = ?').run(item.item_id);
  res.json({ removed: item.item_id });
});

app.post('/api/sync', async (_req, res) => {
  res.json(await syncAll());
});

// ---------- Dashboard data ----------

app.get('/api/summary', (_req, res) => {
  const accounts = db
    .prepare(
      `SELECT a.*, i.institution_name FROM accounts a
       JOIN items i USING (item_id)
       ORDER BY a.type, i.institution_name, a.name`
    )
    .all();

  let assets = 0, debts = 0;
  for (const a of accounts) {
    const bal = a.current ?? 0;
    if (a.type === 'credit' || a.type === 'loan') debts += bal;
    else assets += bal;
  }
  res.json({ assets, debts, net_worth: assets - debts, accounts });
});

app.get('/api/months', (_req, res) => {
  const rows = db
    .prepare("SELECT DISTINCT substr(date,1,7) AS month FROM transactions ORDER BY month DESC")
    .all();
  res.json(rows.map((r) => r.month));
});

app.get('/api/spending', (req, res) => {
  const month = req.query.month;
  if (!/^\d{4}-\d{2}$/.test(month || '')) {
    return res.status(400).json({ error_message: 'month must look like 2026-09' });
  }

  const byCategory = db
    .prepare(
      `SELECT COALESCE(t.category,'OTHER') AS category, SUM(t.amount) AS total, COUNT(*) AS count
       FROM transactions t
       WHERE t.amount > 0 AND t.pending = 0 AND substr(t.date,1,7) = ? AND ${notSpendingSql}
       GROUP BY 1 ORDER BY total DESC`
    )
    .all(month, ...NOT_SPENDING);

  const topMerchants = db
    .prepare(
      `SELECT COALESCE(t.merchant_name, t.name) AS merchant, SUM(t.amount) AS total,
              COUNT(*) AS count, MAX(t.logo_url) AS logo_url
       FROM transactions t
       WHERE t.amount > 0 AND t.pending = 0 AND substr(t.date,1,7) = ? AND ${notSpendingSql}
       GROUP BY 1 ORDER BY total DESC LIMIT 8`
    )
    .all(month, ...NOT_SPENDING);

  const income =
    db
      .prepare(
        `SELECT SUM(-amount) AS total FROM transactions
         WHERE amount < 0 AND category = 'INCOME' AND substr(date,1,7) = ?`
      )
      .get(month).total || 0;

  const trend = db
    .prepare(
      `SELECT substr(t.date,1,7) AS month, SUM(t.amount) AS total
       FROM transactions t
       WHERE t.amount > 0 AND t.pending = 0 AND ${notSpendingSql} AND substr(t.date,1,7) <= ?
       GROUP BY 1 ORDER BY 1 DESC LIMIT 6`
    )
    .all(...NOT_SPENDING, month)
    .reverse();

  const total = byCategory.reduce((s, c) => s + c.total, 0);
  res.json({ month, total, income, byCategory, topMerchants, trend });
});

app.get('/api/recurring', (_req, res) => {
  const rows = db
    .prepare(
      `SELECT r.*, a.name AS account_name, a.mask FROM recurring r
       LEFT JOIN accounts a USING (account_id)
       WHERE r.is_active = 1 AND r.status != 'TOMBSTONED'
       ORDER BY r.average_amount DESC`
    )
    .all()
    .map((r) => ({ ...r, monthly: r.average_amount * (MONTHLY_FACTOR[r.frequency] ?? 1) }));

  const outflows = rows.filter((r) => r.direction === 'outflow');
  const inflows = rows.filter((r) => r.direction === 'inflow');
  res.json({
    outflows,
    inflows,
    monthly_out: outflows.reduce((s, r) => s + r.monthly, 0),
    monthly_in: inflows.reduce((s, r) => s + r.monthly, 0),
  });
});

app.get('/api/transactions', (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 500);
  res.json(
    db
      .prepare(
        `SELECT t.*, a.name AS account_name, a.mask FROM transactions t
         JOIN accounts a USING (account_id)
         ORDER BY t.date DESC, t.transaction_id LIMIT ?`
      )
      .all(limit)
  );
});

// ---------- Webhooks ----------
// Plaid calls this when new data is ready. Needs a public URL (e.g. ngrok) set as PLAID_WEBHOOK_URL.
// Before production, verify the Plaid-Verification JWT header:
// https://plaid.com/docs/api/webhooks/webhook-verification/
app.post('/api/webhook', async (req, res) => {
  const { webhook_type, webhook_code, item_id } = req.body;
  res.sendStatus(200); // acknowledge fast, then work
  const shouldSync =
    (webhook_type === 'TRANSACTIONS' &&
      ['SYNC_UPDATES_AVAILABLE', 'RECURRING_TRANSACTIONS_UPDATE'].includes(webhook_code)) ||
    (webhook_type === 'LIABILITIES' && webhook_code === 'DEFAULT_UPDATE');
  if (!shouldSync) return;
  const item = db.prepare('SELECT * FROM items WHERE item_id = ?').get(item_id);
  if (item) syncItem(item).catch((e) => console.error('[webhook sync]', e?.response?.data || e));
});

// ---------- Errors ----------
app.use((err, _req, res, _next) => {
  const body = plaidErrorBody(err);
  console.error('[error]', body);
  res.status(err?.response?.status || 500).json(body);
});

const port = Number(process.env.PORT) || 8000;
app.listen(port, () => console.log(`Pocketbook API on http://localhost:${port}`));
