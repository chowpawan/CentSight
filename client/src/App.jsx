import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';
import { getToken, consumeRedirect } from './auth.js';
import { usePhone } from './useMediaQuery.js';
import SignIn from './components/SignIn.jsx';
import UserMenu from './components/UserMenu.jsx';
import ConnectButton from './components/ConnectButton.jsx';
import BottomNav from './components/BottomNav.jsx';
import InstallHint from './components/InstallHint.jsx';
import Overview from './components/Overview.jsx';
import Accounts from './components/Accounts.jsx';
import Spending from './components/Spending.jsx';
import Recurring from './components/Recurring.jsx';
import Transactions from './components/Transactions.jsx';

export default function App() {
  const phone = usePhone();
  const [tab, setTab] = useState('home');
  // Pick up a token the backend put in the URL fragment before anything else renders.
  const [authError] = useState(() => consumeRedirect().error);
  const [signedIn, setSignedIn] = useState(() => !!getToken());
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState(null);
  const [items, setItems] = useState([]);
  const [summary, setSummary] = useState(null);
  const [months, setMonths] = useState([]);
  const [month, setMonth] = useState(null);
  const [spending, setSpending] = useState(null);
  const [recurring, setRecurring] = useState(null);
  const [txs, setTxs] = useState([]);

  const load = useCallback(async () => {
    try {
      const [me, it, sum, mo, rec, tx] = await Promise.all([
        api.me(), api.items(), api.summary(), api.months(), api.recurring(), api.transactions(100),
      ]);
      setUser(me);
      setItems(it);
      setSummary(sum);
      setMonths(mo);
      setRecurring(rec);
      setTxs(tx);
      setMonth((m) => (m && mo.includes(m) ? m : mo[0] || null));
      setError(null);
    } catch (e) {
      if (e.status === 401) { setSignedIn(false); return; }
      setError(`Couldn't reach the server: ${e.message}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { if (signedIn) load(); else setLoading(false); }, [load, signedIn]);

  useEffect(() => {
    if (!month) { setSpending(null); return; }
    api.spending(month).then(setSpending).catch((e) => setError(e.message));
  }, [month, txs]);

  // Shadow under the sticky header once the page scrolls.
  useEffect(() => {
    const onScroll = () => {
      document.querySelector('.topbar')?.classList.toggle('is-stuck', window.scrollY > 4);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Jumping between tabs should start at the top, like a native app.
  useEffect(() => { window.scrollTo(0, 0); }, [tab]);

  const refresh = async () => {
    setSyncing(true);
    try {
      const results = await api.sync();
      const failed = results.filter((r) => r.error);
      if (failed.length) setError(`${failed.length} connection(s) failed to refresh (${failed[0].error}).`);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setSyncing(false);
    }
  };

  const onConnected = async () => {
    await load();
    // Plaid often finishes preparing transactions a few seconds after linking.
    setTimeout(refresh, 8000);
  };

  const disconnect = async (item) => {
    if (!confirm(`Disconnect ${item.institution_name || 'this bank'}? Its accounts and transactions will be removed.`)) return;
    try {
      await api.removeItem(item.item_id);
      await load();
    } catch (e) {
      setError(e.message);
    }
  };

  if (!signedIn) return <SignIn error={authError} />;
  if (loading) return <div className="shell"><p className="muted">Loading…</p></div>;

  const empty = items.length === 0;
  const showTabs = phone && !empty;

  const connections = (
    <section className="panel connections">
      <h2>Connected banks</h2>
      <ul>
        {items.map((i) => (
          <li key={i.item_id}>
            <span>{i.institution_name || 'Unnamed bank'}</span>
            <span className="muted small">
              {i.last_synced_at ? `Updated ${new Date(i.last_synced_at).toLocaleString()}` : 'Not synced yet'}
            </span>
            <button className="link-btn" onClick={() => disconnect(i)}>Disconnect</button>
          </li>
        ))}
      </ul>
    </section>
  );

  const sections = {
    home: (
      <>
        {summary && <Overview summary={summary} />}
        <Accounts accounts={summary?.accounts || []} />
        {connections}
      </>
    ),
    spending: <Spending months={months} month={month} setMonth={setMonth} data={month ? spending : null} />,
    recurring: <Recurring data={recurring} />,
    activity: <Transactions rows={txs} />,
  };

  return (
    <div className={showTabs ? 'has-tabs' : undefined}>
      <div className="shell">
        <header className="topbar">
          <span className="brand">CentSight</span>
          <div className="topbar-actions">
            {!empty && (
              <>
                <button className="btn btn-quiet" onClick={refresh} disabled={syncing}>
                  {syncing ? 'Refreshing…' : 'Refresh'}
                </button>
                <ConnectButton variant="quiet" onConnected={onConnected} onError={setError}>Add</ConnectButton>
              </>
            )}
            <UserMenu user={user} />
          </div>
        </header>

        <InstallHint />

        {error && (
          <div className="alert" role="alert">
            <span>{error}</span>
            <button className="icon-btn" onClick={() => setError(null)} aria-label="Dismiss">×</button>
          </div>
        )}

        {empty ? (
          <section className="empty">
            <h1>See every balance and bill in one place.</h1>
            <p>Connect a bank or credit card to pull in balances, transactions and recurring charges. In sandbox mode, sign in with <code>user_good</code> and <code>pass_good</code>.</p>
            <ConnectButton onConnected={onConnected} onError={setError} />
          </section>
        ) : showTabs ? (
          sections[tab]
        ) : (
          <>
            {summary && <Overview summary={summary} />}
            <div className="grid">
              <div className="col">
                <Accounts accounts={summary?.accounts || []} />
                {connections}
              </div>
              <div className="col">
                <Spending months={months} month={month} setMonth={setMonth} data={month ? spending : null} />
              </div>
            </div>
            <Recurring data={recurring} />
            <Transactions rows={txs} />
          </>
        )}
      </div>

      {showTabs && <BottomNav tab={tab} setTab={setTab} />}
    </div>
  );
}
