import { money, shortDate, daysUntil } from '../format.js';

function CardRow({ a }) {
  const util = a.credit_limit ? Math.min(a.current / a.credit_limit, 1) : null;
  const days = daysUntil(a.due_date);
  const dueSoon = days !== null && days >= 0 && days <= 7;

  return (
    <li className="acct">
      <div className="acct-main">
        <div>
          <div className="acct-name">{a.name}{a.mask && <span className="mask"> ••{a.mask}</span>}</div>
          <div className="acct-inst">{a.institution_name}</div>
        </div>
        <div className="acct-bal neg">{money(a.current)}</div>
      </div>
      {util !== null && (
        <div className="util">
          <div className="util-track">
            <div className={`util-fill ${util > 0.3 ? 'util-high' : ''}`} style={{ width: `${util * 100}%` }} />
          </div>
          <span className="util-label">{Math.round(util * 100)}% of {money(a.credit_limit)}</span>
        </div>
      )}
      {(a.due_date || a.min_payment != null) && (
        <div className={`acct-due ${dueSoon ? 'due-soon' : ''}`}>
          {a.min_payment != null && <>Minimum {money(a.min_payment)}</>}
          {a.due_date && <> due {shortDate(a.due_date)}{dueSoon && ` (in ${days} day${days === 1 ? '' : 's'})`}</>}
          {a.apr != null && <span className="apr">{a.apr}% APR</span>}
        </div>
      )}
    </li>
  );
}

function CashRow({ a }) {
  return (
    <li className="acct">
      <div className="acct-main">
        <div>
          <div className="acct-name">{a.name}{a.mask && <span className="mask"> ••{a.mask}</span>}</div>
          <div className="acct-inst">{a.institution_name}</div>
        </div>
        <div className="acct-bal">
          {money(a.current)}
          {a.available != null && a.available !== a.current && (
            <div className="acct-avail">{money(a.available)} available</div>
          )}
        </div>
      </div>
    </li>
  );
}

export default function Accounts({ accounts }) {
  const cash = accounts.filter((a) => a.type === 'depository' || a.type === 'investment');
  const cards = accounts.filter((a) => a.type === 'credit');
  const loans = accounts.filter((a) => a.type === 'loan');
  const other = accounts.filter((a) => !['depository', 'investment', 'credit', 'loan'].includes(a.type));

  return (
    <section className="panel" aria-labelledby="accounts-heading">
      <h2 id="accounts-heading">Accounts</h2>
      {cards.length > 0 && (
        <>
          <h3>Credit cards</h3>
          <ul className="acct-list">{cards.map((a) => <CardRow key={a.account_id} a={a} />)}</ul>
        </>
      )}
      {cash.length > 0 && (
        <>
          <h3>Bank and investments</h3>
          <ul className="acct-list">{cash.map((a) => <CashRow key={a.account_id} a={a} />)}</ul>
        </>
      )}
      {loans.length > 0 && (
        <>
          <h3>Loans</h3>
          <ul className="acct-list">{loans.map((a) => <CardRow key={a.account_id} a={a} />)}</ul>
        </>
      )}
      {other.length > 0 && (
        <>
          <h3>Other</h3>
          <ul className="acct-list">{other.map((a) => <CashRow key={a.account_id} a={a} />)}</ul>
        </>
      )}
    </section>
  );
}
