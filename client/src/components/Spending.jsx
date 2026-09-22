import { money, moneyRound, categoryLabel, monthLabel } from '../format.js';

export default function Spending({ months, month, setMonth, data }) {
  const idx = months.indexOf(month);
  const older = months[idx + 1];
  const newer = months[idx - 1];
  const max = data?.by_category[0]?.total || 1;
  const trendMax = Math.max(...(data?.trend || []).map((t) => t.total), 1);

  return (
    <section className="panel" aria-labelledby="spending-heading">
      <div className="panel-head">
        <h2 id="spending-heading">Spending</h2>
        <div className="month-nav">
          <button className="icon-btn" onClick={() => setMonth(older)} disabled={!older} aria-label="Previous month">‹</button>
          <span className="month-name">{month ? monthLabel(month) : '—'}</span>
          <button className="icon-btn" onClick={() => setMonth(newer)} disabled={!newer} aria-label="Next month">›</button>
        </div>
      </div>

      {!data ? (
        <p className="muted">No transactions yet. Plaid can take a minute to prepare them after you connect.</p>
      ) : (
        <>
          <p className="spend-total">
            <strong>{money(data.total)}</strong> spent
            {data.income > 0 && <> against <strong className="pos">{money(data.income)}</strong> in income</>}
          </p>

          {data.trend.length > 1 && (
            <div className="trend" aria-label="Spending over the last months">
              {data.trend.map((t) => (
                <button key={t.month} className={`trend-col ${t.month === month ? 'is-current' : ''}`}
                  onClick={() => setMonth(t.month)} title={`${monthLabel(t.month)}: ${money(t.total)}`}>
                  <span className="trend-bar-wrap"><span className="trend-bar" style={{ height: `${(t.total / trendMax) * 100}%` }} /></span>
                  <span className="trend-label">{monthLabel(t.month, 'short')}</span>
                </button>
              ))}
            </div>
          )}

          <h3>By category</h3>
          <ul className="cat-list">
            {data.by_category.map((c) => (
              <li key={c.category} className="cat">
                <div className="cat-row">
                  <span>{categoryLabel(c.category)}</span>
                  <span className="num">{money(c.total)}</span>
                </div>
                <div className="cat-track"><div className="cat-fill" style={{ width: `${(c.total / max) * 100}%` }} /></div>
              </li>
            ))}
          </ul>

          <h3>Where it went</h3>
          <ol className="merchants">
            {data.top_merchants.map((m) => (
              <li key={m.merchant}>
                {m.logo_url ? <img src={m.logo_url} alt="" className="logo" /> : <span className="logo logo-blank">{m.merchant?.[0]}</span>}
                <span className="merchant-name">{m.merchant}</span>
                <span className="muted small">{m.count}×</span>
                <span className="num">{moneyRound(m.total)}</span>
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
