import { API_BASE, getToken, clearToken } from './auth.js';

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function request(path, options = {}) {
  const token = getToken();
  const res = await fetch(`${API_BASE}/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  // An expired or revoked session should drop us back to the sign-in screen, not show an error.
  if (res.status === 401) {
    clearToken();
    throw new ApiError('Your session expired. Please sign in again.', 401);
  }

  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(
      body.error_message || body.display_message || `Request failed (${res.status})`,
      res.status
    );
  }
  return body;
}

export const api = {
  me: () => request('/me'),
  linkToken: () => request('/link/token', { method: 'POST' }),
  exchange: (public_token, institution_name) =>
    request('/link/exchange', { method: 'POST', body: JSON.stringify({ public_token, institution_name }) }),
  sync: () => request('/sync', { method: 'POST' }),
  items: () => request('/items'),
  removeItem: (id) => request(`/items/${id}`, { method: 'DELETE' }),
  summary: () => request('/summary'),
  months: () => request('/months'),
  spending: (month) => request(`/spending?month=${month}`),
  recurring: () => request('/recurring'),
  transactions: (limit = 40) => request(`/transactions?limit=${limit}`),
};

export { ApiError };
