import { useEffect, useState } from 'react';
import { usePlaidLink } from 'react-plaid-link';
import { api } from '../api.js';

// Fetches a link token on click, then opens Plaid Link as soon as it's ready.
export default function ConnectButton({ onConnected, onError, variant = 'primary', children }) {
  const [token, setToken] = useState(null);
  const [busy, setBusy] = useState(false);

  const { open, ready } = usePlaidLink({
    token,
    onSuccess: async (public_token, metadata) => {
      try {
        setBusy(true);
        await api.exchange(public_token, metadata.institution?.name);
        onConnected?.();
      } catch (e) {
        onError?.(e.message);
      } finally {
        setBusy(false);
        setToken(null);
      }
    },
    onExit: () => {
      setBusy(false);
      setToken(null);
    },
  });

  useEffect(() => {
    if (token && ready) open();
  }, [token, ready, open]);

  const start = async () => {
    try {
      setBusy(true);
      const { link_token } = await api.linkToken();
      setToken(link_token);
    } catch (e) {
      setBusy(false);
      onError?.(e.message);
    }
  };

  return (
    <button className={`btn btn-${variant}`} onClick={start} disabled={busy}>
      {busy ? 'Connecting…' : children || 'Connect an account'}
    </button>
  );
}
