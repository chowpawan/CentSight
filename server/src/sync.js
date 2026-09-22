import db from './db.js';
import { plaid, plaidErrorCode } from './plaid.js';
import { decrypt } from './crypto.js';

// Errors that just mean "this product isn't available for this institution yet".
const SOFT_ERRORS = new Set([
  'PRODUCTS_NOT_SUPPORTED',
  'PRODUCT_NOT_READY',
  'NO_LIABILITY_ACCOUNTS',
  'ADDITIONAL_CONSENT_REQUIRED',
]);

function soft(err, label) {
  const code = plaidErrorCode(err);
  if (SOFT_ERRORS.has(code)) {
    console.log(`[sync] ${label}: skipped (${code})`);
    return;
  }
  throw err;
}

const upsertAccount = db.prepare(`
  INSERT INTO accounts (account_id, item_id, name, official_name, mask, type, subtype,
                        current, available, credit_limit, currency, updated_at)
  VALUES (@account_id, @item_id, @name, @official_name, @mask, @type, @subtype,
          @current, @available, @credit_limit, @currency, datetime('now'))
  ON CONFLICT(account_id) DO UPDATE SET
    name=excluded.name, official_name=excluded.official_name, mask=excluded.mask,
    type=excluded.type, subtype=excluded.subtype, current=excluded.current,
    available=excluded.available, credit_limit=excluded.credit_limit,
    currency=excluded.currency, updated_at=excluded.updated_at
`);

async function syncAccounts(itemId, accessToken) {
  // /accounts/get returns cached balances (free, fast).
  // Swap for plaid.accountsBalanceGet for real-time balances (billed per call).
  const { data } = await plaid.accountsGet({ access_token: accessToken });
  db.transaction(() => {
    for (const a of data.accounts) {
      upsertAccount.run({
        account_id: a.account_id,
        item_id: itemId,
        name: a.name,
        official_name: a.official_name,
        mask: a.mask,
        type: a.type,
        subtype: a.subtype,
        current: a.balances.current,
        available: a.balances.available,
        credit_limit: a.balances.limit,
        currency: a.balances.iso_currency_code || a.balances.unofficial_currency_code,
      });
    }
  })();
  return data.item?.institution_name;
}

const updateLiability = db.prepare(`
  UPDATE accounts SET statement_balance=?, min_payment=?, due_date=?, apr=? WHERE account_id=?
`);

async function syncLiabilities(accessToken) {
  try {
    const { data } = await plaid.liabilitiesGet({ access_token: accessToken });
    for (const c of data.liabilities?.credit || []) {
      const purchaseApr = c.aprs?.find((x) => x.apr_type === 'purchase_apr') || c.aprs?.[0];
      updateLiability.run(
        c.last_statement_balance,
        c.minimum_payment_amount,
        c.next_payment_due_date,
        purchaseApr?.apr_percentage ?? null,
        c.account_id
      );
    }
  } catch (err) {
    soft(err, 'liabilities');
  }
}

const upsertTx = db.prepare(`
  INSERT INTO transactions (transaction_id, account_id, item_id, date, name, merchant_name,
                            amount, category, category_detailed, pending, logo_url)
  VALUES (@transaction_id, @account_id, @item_id, @date, @name, @merchant_name,
          @amount, @category, @category_detailed, @pending, @logo_url)
  ON CONFLICT(transaction_id) DO UPDATE SET
    date=excluded.date, name=excluded.name, merchant_name=excluded.merchant_name,
    amount=excluded.amount, category=excluded.category,
    category_detailed=excluded.category_detailed, pending=excluded.pending,
    logo_url=excluded.logo_url
`);
const deleteTx = db.prepare('DELETE FROM transactions WHERE transaction_id = ?');
const saveCursor = db.prepare(
  "UPDATE items SET cursor = ?, last_synced_at = datetime('now') WHERE item_id = ?"
);

async function syncTransactions(item, accessToken) {
  const startCursor = item.cursor || null;
  let cursor = startCursor;
  let added = [], modified = [], removed = [];
  let hasMore = true;

  while (hasMore) {
    try {
      const { data } = await plaid.transactionsSync({
        access_token: accessToken,
        count: 500,
        ...(cursor ? { cursor } : {}),
      });
      added.push(...data.added);
      modified.push(...data.modified);
      removed.push(...data.removed);
      cursor = data.next_cursor;
      hasMore = data.has_more;
    } catch (err) {
      if (plaidErrorCode(err) === 'TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION') {
        // Data changed mid-pagination: restart from the last saved cursor.
        cursor = startCursor;
        added = []; modified = []; removed = [];
        hasMore = true;
        continue;
      }
      throw err;
    }
  }

  db.transaction(() => {
    for (const t of [...added, ...modified]) {
      upsertTx.run({
        transaction_id: t.transaction_id,
        account_id: t.account_id,
        item_id: item.item_id,
        date: t.date,
        name: t.name,
        merchant_name: t.merchant_name,
        amount: t.amount,
        category: t.personal_finance_category?.primary ?? null,
        category_detailed: t.personal_finance_category?.detailed ?? null,
        pending: t.pending ? 1 : 0,
        logo_url: t.logo_url ?? null,
      });
    }
    for (const r of removed) deleteTx.run(r.transaction_id);
    saveCursor.run(cursor, item.item_id);
  })();

  return { added: added.length, modified: modified.length, removed: removed.length };
}

const clearRecurring = db.prepare('DELETE FROM recurring WHERE item_id = ?');
const insertRecurring = db.prepare(`
  INSERT OR REPLACE INTO recurring (stream_id, item_id, account_id, direction, description,
    merchant_name, frequency, average_amount, last_amount, last_date, next_date,
    is_active, status, category)
  VALUES (@stream_id, @item_id, @account_id, @direction, @description, @merchant_name,
    @frequency, @average_amount, @last_amount, @last_date, @next_date, @is_active,
    @status, @category)
`);

async function syncRecurring(itemId, accessToken) {
  try {
    const { data } = await plaid.transactionsRecurringGet({ access_token: accessToken });
    const rows = [
      ...data.inflow_streams.map((s) => ({ s, direction: 'inflow' })),
      ...data.outflow_streams.map((s) => ({ s, direction: 'outflow' })),
    ];
    db.transaction(() => {
      clearRecurring.run(itemId);
      for (const { s, direction } of rows) {
        insertRecurring.run({
          stream_id: s.stream_id,
          item_id: itemId,
          account_id: s.account_id,
          direction,
          description: s.description,
          merchant_name: s.merchant_name ?? null,
          frequency: s.frequency,
          average_amount: Math.abs(s.average_amount?.amount ?? 0),
          last_amount: Math.abs(s.last_amount?.amount ?? 0),
          last_date: s.last_date,
          next_date: s.predicted_next_date ?? null,
          is_active: s.is_active ? 1 : 0,
          status: s.status,
          category: s.personal_finance_category?.primary ?? null,
        });
      }
    })();
  } catch (err) {
    soft(err, 'recurring');
  }
}

export async function syncItem(item) {
  const accessToken = decrypt(item.access_token);
  const institution = await syncAccounts(item.item_id, accessToken);
  await syncLiabilities(accessToken);
  const tx = await syncTransactions(item, accessToken);
  await syncRecurring(item.item_id, accessToken);
  return { item_id: item.item_id, institution, transactions: tx };
}

export async function syncAll() {
  const items = db.prepare('SELECT * FROM items').all();
  const results = [];
  for (const item of items) {
    try {
      results.push(await syncItem(item));
    } catch (err) {
      console.error(`[sync] item ${item.item_id} failed`, err?.response?.data || err);
      results.push({ item_id: item.item_id, error: err?.response?.data?.error_code || 'SYNC_FAILED' });
    }
  }
  return results;
}
