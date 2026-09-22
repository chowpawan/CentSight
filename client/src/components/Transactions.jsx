import { useMemo, useState } from 'react';
import { money, shortDate, categoryLabel } from '../format.js';

const PAGE = 25;

// Groups by date so a long list reads as days rather than one endless column.
function groupByDate(rows) {
  const out = [];
  for (const t of rows) {
    const last = out[out.length - 1];
    if (last && last.date === t.date) last.rows.push(t);
    else out.push({ date: t.date, rows: [t] });
  }
  return out;
}

export default function Transactions({ rows }) {
  const [shown, setShown] = useState(PAGE);
  const visible = useMemo(() => rows.slice(0, shown), [rows, shown]);
  const groups = useMemo(() => groupByDate(visible), [visible]);

  return (
    <section className="panel" aria-labelledby="tx-heading">
      <h2 id="tx-heading">Recent transactions</h2>
      {rows.length === 0 ? (
        <p className="muted">Nothing here yet.</p>
      ) : (
        <>
          {groups.map((g) => (
            <div key={g.date}>
              <h3 className="tx-group">{shortDate(g.date)}</h3>
              <ul className="tx-list">
                {g.rows.map((t) => {
                  const incoming = t.amount < 0;
                  return (
                    <li key={t.transaction_id} className="tx">
                      <span className="tx-body">
                        <span>{t.merchant_name || t.name}{t.pending ? <em className="pending"> pending</em> : null}</span>
                        <span className="muted small">{categoryLabel(t.category)} · {t.account_name}</span>
                      </span>
                      <span className={`num ${incoming ? 'pos' : ''}`}>{incoming ? '+' : ''}{money(Math.abs(t.amount))}</span>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
          {shown < rows.length && (
            <button className="show-more" onClick={() => setShown((n) => n + PAGE)}>
              Show {Math.min(PAGE, rows.length - shown)} more
            </button>
          )}
        </>
      )}
    </section>
  );
}
