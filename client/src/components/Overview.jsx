import { moneyRound } from '../format.js';

// A balance sheet in one line: what you have, what you owe, and the gap.
export default function Overview({ summary }) {
  const { assets, debts, net_worth } = summary;
  const total = assets + debts || 1;
  const assetPct = (assets / total) * 100;

  return (
    <section className="overview" aria-labelledby="overview-heading">
      <h1 id="overview-heading" className="overview-sentence">
        You have <span className="pos">{moneyRound(assets)}</span> and owe{' '}
        <span className="neg">{moneyRound(debts)}</span>, leaving{' '}
        <span className={net_worth >= 0 ? 'net' : 'neg'}>{moneyRound(net_worth)}</span>.
      </h1>
      <div className="sheet-bar" role="img"
        aria-label={`Assets ${moneyRound(assets)}, debts ${moneyRound(debts)}`}>
        <div className="sheet-assets" style={{ width: `${assetPct}%` }} />
        <div className="sheet-debts" style={{ width: `${100 - assetPct}%` }} />
      </div>
      <div className="sheet-legend">
        <span><i className="dot dot-pos" /> Cash and investments</span>
        <span><i className="dot dot-neg" /> Cards and loans</span>
      </div>
    </section>
  );
}
