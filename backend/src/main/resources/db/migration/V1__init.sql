-- CentSight schema.
-- Every table that holds financial data carries user_id and is queried through it,
-- so one signed-in person can never read another's accounts.

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    google_sub  TEXT UNIQUE NOT NULL,
    email       TEXT NOT NULL,
    name        TEXT,
    picture_url TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login  TIMESTAMPTZ
);

CREATE TABLE plaid_items (
    item_id          TEXT PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token     TEXT NOT NULL,             -- AES-256-GCM ciphertext, never plaintext
    institution_name TEXT,
    cursor           TEXT,                      -- /transactions/sync cursor
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at   TIMESTAMPTZ
);
CREATE INDEX idx_items_user ON plaid_items(user_id);

CREATE TABLE accounts (
    account_id        TEXT PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id           TEXT NOT NULL REFERENCES plaid_items(item_id) ON DELETE CASCADE,
    name              TEXT,
    official_name     TEXT,
    mask              TEXT,
    type              TEXT,
    subtype           TEXT,
    current_balance   NUMERIC(19,4),
    available_balance NUMERIC(19,4),
    credit_limit      NUMERIC(19,4),
    currency          TEXT,
    statement_balance NUMERIC(19,4),
    min_payment       NUMERIC(19,4),
    due_date          DATE,
    apr               NUMERIC(9,4),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user ON accounts(user_id);
CREATE INDEX idx_accounts_item ON accounts(item_id);

CREATE TABLE transactions (
    transaction_id    TEXT PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id           TEXT NOT NULL REFERENCES plaid_items(item_id) ON DELETE CASCADE,
    account_id        TEXT NOT NULL,
    tx_date           DATE NOT NULL,
    name              TEXT,
    merchant_name     TEXT,
    amount            NUMERIC(19,4) NOT NULL,   -- Plaid sign: positive = money out
    category          TEXT,
    category_detailed TEXT,
    pending           BOOLEAN NOT NULL DEFAULT FALSE,
    logo_url          TEXT
);
CREATE INDEX idx_tx_user_date ON transactions(user_id, tx_date DESC);
CREATE INDEX idx_tx_item ON transactions(item_id);
CREATE INDEX idx_tx_account ON transactions(account_id);

CREATE TABLE recurring_streams (
    stream_id      TEXT PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id        TEXT NOT NULL REFERENCES plaid_items(item_id) ON DELETE CASCADE,
    account_id     TEXT,
    direction      TEXT NOT NULL,               -- inflow | outflow
    description    TEXT,
    merchant_name  TEXT,
    frequency      TEXT,
    average_amount NUMERIC(19,4),
    last_amount    NUMERIC(19,4),
    last_date      DATE,
    next_date      DATE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    status         TEXT,
    category       TEXT
);
CREATE INDEX idx_recurring_user ON recurring_streams(user_id);
CREATE INDEX idx_recurring_item ON recurring_streams(item_id);
