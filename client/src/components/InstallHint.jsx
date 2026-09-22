import { useEffect, useState } from 'react';

const DISMISSED = 'centsight:install-dismissed';

// Android/Chrome fire beforeinstallprompt, so we can offer a real install button.
// iOS Safari has no such event, so we show the Share -> Add to Home Screen hint instead.
export default function InstallHint() {
  const [prompt, setPrompt] = useState(null);
  const [iosHint, setIosHint] = useState(false);

  useEffect(() => {
    try {
      if (localStorage.getItem(DISMISSED)) return;
    } catch { /* private mode: just show it */ }

    const standalone =
      window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
    if (standalone) return;

    const onPrompt = (e) => { e.preventDefault(); setPrompt(e); };
    window.addEventListener('beforeinstallprompt', onPrompt);

    const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
    if (isIos) setIosHint(true);

    return () => window.removeEventListener('beforeinstallprompt', onPrompt);
  }, []);

  const dismiss = () => {
    try { localStorage.setItem(DISMISSED, '1'); } catch { /* ignore */ }
    setPrompt(null);
    setIosHint(false);
  };

  const install = async () => {
    if (!prompt) return;
    prompt.prompt();
    await prompt.userChoice;
    dismiss();
  };

  if (!prompt && !iosHint) return null;

  return (
    <div className="install-hint">
      {prompt ? (
        <>
          <span>Add CentSight to your home screen.</span>
          <button className="btn btn-primary" onClick={install}>Install</button>
        </>
      ) : (
        <span>Add to your home screen: tap <strong>Share</strong>, then <strong>Add to Home Screen</strong>.</span>
      )}
      <button className="icon-btn" onClick={dismiss} aria-label="Dismiss">×</button>
    </div>
  );
}
