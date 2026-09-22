import Database from 'better-sqlite3';

const db = new Database(process.env.DB_PATH || 'finance.db');
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
CREATE TABLE IF NOT EXISTS items (
  item_id          TEXT PRIMARY KEY,
  access_token     TEXT NOT NULL,          -- encrypted
  institution_name TEXT,
  cursor           TEXT,                   -- /transactions/sync cursor
  created_at       TEXT DEFAULT (datetime('now')),
  last_synced_at   TEXT
);

CREATE TABLE IF NOT EXISTS accounts (
  account_id       TEXT PRIMARY KEY,
  item_id          TEXT NOT NULL REFERENCES items(item_id) ON DELETE CASCADE,
  name             TEXT,
  official_name    TEXT,
  mask             TEXT,
  type             TEXT,                   -- depository | credit | loan | investment | other
  subtype          TEXT,
  current          REAL,
  available        REAL,
  credit_limit     REAL,
  currency         TEXT,
  statement_balance REAL,
  min_payment      REAL,
  due_date         TEXT,
  apr              REAL,
  updated_at       TEXT
);

CREATE TABLE IF NOT EXISTS transactions (
  transaction_id    TEXT PRIMARY KEY,
  account_id        TEXT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
  item_id           TEXT NOT NULL,
  date              TEXT NOT NULL,
  name              TEXT,
  merchant_name     TEXT,
  amount            REAL NOT NULL,         -- Plaid: positive = money out
  category          TEXT,                  -- personal_finance_category.primary
  category_detailed TEXT,
  pending           INTEGER DEFAULT 0,
  logo_url          TEXT
);
CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(date);

CREATE TABLE IF NOT EXISTS recurring (
  stream_id      TEXT PRIMARY KEY,
  item_id        TEXT NOT NULL REFERENCES items(item_id) ON DELETE CASCADE,
  account_id     TEXT,
  direction      TEXT,                     -- inflow | outflow
  description    TEXT,
  merchant_name  TEXT,
  frequency      TEXT,
  average_amount REAL,
  last_amount    REAL,
  last_date      TEXT,
  next_date      TEXT,
  is_active      INTEGER,
  status         TEXT,
  category       TEXT
);
`);

export default db;
