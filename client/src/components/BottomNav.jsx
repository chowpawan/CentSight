const TABS = [
  { id: 'home', label: 'Home', icon: 'M3 10.5 12 3l9 7.5M5.5 9.5V20h13V9.5' },
  { id: 'spending', label: 'Spending', icon: 'M4 19V9m5 10V5m5 14v-7m5 7V8' },
  { id: 'recurring', label: 'Recurring', icon: 'M4 12a8 8 0 0 1 13.7-5.7M20 12a8 8 0 0 1-13.7 5.7M17 3v4h-4M7 21v-4h4' },
  { id: 'activity', label: 'Activity', icon: 'M4 7h16M4 12h16M4 17h10' },
];

export default function BottomNav({ tab, setTab }) {
  return (
    <nav className="bottom-nav" aria-label="Sections">
      {TABS.map((t) => (
        <button
          key={t.id}
          className={`nav-item${tab === t.id ? ' is-active' : ''}`}
          onClick={() => setTab(t.id)}
          aria-current={tab === t.id ? 'page' : undefined}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d={t.icon} />
          </svg>
          <span>{t.label}</span>
        </button>
      ))}
    </nav>
  );
}
