import { useEffect, useRef, useState } from 'react';
import { signOut } from '../auth.js';

export default function UserMenu({ user }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const initial = (user?.name || user?.email || '?').trim()[0]?.toUpperCase();

  return (
    <div className="user-menu" ref={ref}>
      <button
        className="icon-btn"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account"
      >
        {user?.picture_url
          ? <img className="avatar" src={user.picture_url} alt="" referrerPolicy="no-referrer" />
          : <span className="avatar logo-blank" aria-hidden="true">{initial}</span>}
      </button>

      {open && (
        <div className="menu-pop" role="menu">
          <div className="who">{user?.email}</div>
          <button role="menuitem" onClick={signOut}>Sign out</button>
        </div>
      )}
    </div>
  );
}
