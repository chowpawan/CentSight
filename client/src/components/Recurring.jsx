import { money, shortDate, frequencyLabel, categoryLabel } from '../format.js';

export default function Recurring({ data }) {
  if (!data) return null;
  const { outflows, inflows, monthly_out, monthly_in } = data;

  return (
    <section className="panel recurring" aria-labelledby="recurring-heading">
      <h2 id="recurring-heading">Recurring</h2>
      {outflows.length === 0 && inflows.length === 0 ? (
        <p className="muted">Plaid needs a few months of history to spot repeating charges. Check back after the next sync.</p>
      ) : (
        <>
          <p className="spend-total">
            About <strong>{money(monthly_out)}</strong> a month goes out automatically
            {monthly_in > 0 && <>, and <strong className="pos">{money(monthly_in)}</strong> comes in</>}.
          </p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Charge</th><th>How often</th><th>Next</th><th className="num">Per month</th></tr>
              </thead>
              <tbody>
                {outflows.map((r) => (
                  <tr key={r.stream_id}>
                    <td>
                      <div>{r.merchant_name || r.description}</div>
                      <div className="muted small">{categoryLabel(r.category)} · {r.account_name}</div>
                    </td>
                    <td>{frequencyLabel(r.frequency)} <span className="muted small">({money(r.average_amount)})</span></td>
                    <td>{shortDate(r.next_date) || '—'}</td>
                    <td className="num">{money(r.monthly)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {inflows.length > 0 && (
            <>
              <h3>Coming in</h3>
              <ul className="inflows">
                {inflows.map((r) => (
                  <li key={r.stream_id}>
                    <span>{r.merchant_name || r.description}</span>
                    <span className="muted small">{frequencyLabel(r.frequency)}</span>
                    <span className="num pos">{money(r.average_amount)}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}
