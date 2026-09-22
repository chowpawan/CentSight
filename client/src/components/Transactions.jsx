import { money, shortDate, categoryLabel } from '../format.js';

export default function Transactions({ rows }) {
  return (
    <section className="panel" aria-labelledby="tx-heading">
      <h2 id="tx-heading">Recent transactions</h2>
      {rows.length === 0 ? (
        <p className="muted">Nothing here yet.</p>
      ) : (
        <ul className="tx-list">
          {rows.map((t) => {
            const incoming = t.amount < 0;
            return (
              <li key={t.transaction_id} className="tx">
                <span className="tx-date">{shortDate(t.date)}</span>
                <span className="tx-body">
                  <span>{t.merchant_name || t.name}{t.pending ? <em className="pending"> pending</em> : null}</span>
                  <span className="muted small">{categoryLabel(t.category)} · {t.account_name}</span>
                </span>
                <span className={`num ${incoming ? 'pos' : ''}`}>{incoming ? '+' : ''}{money(Math.abs(t.amount))}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
