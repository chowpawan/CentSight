// Session handling for Google sign-in.
// The backend redirects back to /#token=<jwt> after Google; we stash it and send it as a bearer token.

const KEY = 'centsight:token';

// Same-origin in production (CloudFront routes /api to the API); Vite proxies it in dev.
export const API_BASE = import.meta.env.VITE_API_URL || '';

export function getToken() {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null; // private mode / blocked storage
  }
}

function setToken(token) {
  try {
    localStorage.setItem(KEY, token);
  } catch { /* the session just won't survive a reload */ }
}

export function clearToken() {
  try {
    localStorage.removeItem(KEY);
  } catch { /* ignore */ }
}

/**
 * Reads a token (or error) the backend left in the URL fragment and cleans the address bar,
 * so the JWT is never left sitting in a shareable URL.
 */
export function consumeRedirect() {
  const hash = window.location.hash;
  if (!hash.startsWith('#')) return { error: null };

  const params = new URLSearchParams(hash.slice(1));
  const token = params.get('token');
  const error = params.get('error');

  if (token || error) {
    if (token) setToken(token);
    history.replaceState(null, '', window.location.pathname + window.location.search);
  }
  return { error };
}

export function signIn() {
  window.location.href = `${API_BASE}/oauth2/authorization/google`;
}

export function signOut() {
  clearToken();
  window.location.reload();
}
